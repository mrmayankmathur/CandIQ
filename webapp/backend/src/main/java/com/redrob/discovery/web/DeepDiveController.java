package com.redrob.discovery.web;

import com.redrob.discovery.model.Candidate.CandidateProfile;
import com.redrob.discovery.model.Jd.JobDescription;
import com.redrob.discovery.model.Ranking.RankedResult;
import com.redrob.discovery.service.JdService;
import com.redrob.discovery.service.LlmService;
import com.redrob.discovery.service.ProfileService;
import com.redrob.discovery.service.ResultsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Streams an AI "deep-dive" assessment of one ranked candidate over Server-Sent Events.
 *
 * <p>On connect the backend assembles a grounded prompt (profile + JD intent + engine verdict) and
 * relays OpenAI's streamed tokens as {@code token} events, finishing with {@code done}. If no API key
 * is configured it emits a single {@code info} event and closes — the rest of the app keeps working.
 *
 * <p>SSE plumbing mirrors {@link com.redrob.discovery.service.RunnerService}: a daemon thread drives
 * the work and a {@code send} helper drops the emitter on client disconnect.
 */
@RestController
public class DeepDiveController {

    private static final Logger log = LoggerFactory.getLogger(DeepDiveController.class);

    private final ProfileService profiles;
    private final ResultsService results;
    private final JdService jd;
    private final LlmService llm;

    public DeepDiveController(ProfileService profiles, ResultsService results, JdService jd, LlmService llm) {
        this.profiles = profiles;
        this.results = results;
        this.jd = jd;
        this.llm = llm;
    }

    @GetMapping(value = "/api/candidates/{id}/deepdive", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter deepDive(@PathVariable("id") String id) {
        CandidateProfile profile = profiles.get(id);
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown candidate: " + id);
        }

        // ~10 min ceiling, matching RankRunController; an assessment finishes in seconds.
        SseEmitter emitter = new SseEmitter(600_000L);

        if (!llm.isEnabled()) {
            send(emitter, "info",
                    "AI Deep-Dive is off. Set OPENAI_API_KEY in a repo-root .env (see .env.example) and restart.");
            emitter.complete();
            return emitter;
        }

        RankedResult result = results.byId(id);
        JobDescription jobDescription = jd.get();

        Thread t = new Thread(() -> {
            try {
                llm.streamAssessment(id, profile, jobDescription, result, token -> send(emitter, "token", token));
                send(emitter, "done", "{\"status\":\"complete\"}");
                emitter.complete();
            } catch (Throwable e) {
                // Covers both the live-stream owner and a concurrent waiter whose cached future failed
                // (prior.join() -> CompletionException). Either way the client sees a clean error event.
                log.warn("Deep-dive failed for {}", id, e);
                send(emitter, "error", rootMessage(e));
                emitter.complete();
            }
        }, "deepdive-" + id);
        t.setDaemon(true);
        t.start();

        return emitter;
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // Client disconnected — nothing more to do; the daemon thread will finish and exit.
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : msg;
    }
}
