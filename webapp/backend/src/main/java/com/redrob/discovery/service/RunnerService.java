package com.redrob.discovery.service;

import com.redrob.discovery.config.AppPaths;
import com.redrob.discovery.model.Ranking.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates a live re-run of the frozen Python ranker and streams its stdout to subscribed SSE
 * clients. Single-flight (one run at a time). On a clean exit (code 0) it hot-reloads the results
 * and profile caches so the UI reflects the freshly written submission.csv.
 *
 * <p>The {@code KMP_DUPLICATE_LIB_OK=TRUE} env guard mirrors the ranker's own fix for the
 * faiss/torch OpenMP clash so the subprocess behaves exactly like a direct CLI invocation.
 */
@Service
public class RunnerService {

    private static final Logger log = LoggerFactory.getLogger(RunnerService.class);

    private final AppPaths paths;
    private final ResultsService results;
    private final ProfileService profiles;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final List<String> currentLog = new CopyOnWriteArrayList<>();
    private volatile RunStatus lastRun;
    private volatile String runId;

    public RunnerService(AppPaths paths, ResultsService results, ProfileService profiles) {
        this.paths = paths;
        this.results = results;
        this.profiles = profiles;
    }

    public boolean isRunning() {
        return running.get();
    }

    public RunStatus lastRun() {
        return lastRun;
    }

    /** Start a run; throws {@link IllegalStateException} if one is already in flight. */
    public synchronized String start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A ranking run is already in progress");
        }
        currentLog.clear();
        runId = UUID.randomUUID().toString();
        lastRun = new RunStatus(runId, "running", Instant.now().toString(), null, null);
        Thread t = new Thread(this::execute, "ranker-run-" + runId);
        t.setDaemon(true);
        t.start();
        return runId;
    }

    /** Register an SSE client; replays any logs already produced by the in-flight run. */
    public void subscribe(SseEmitter emitter) {
        for (String line : currentLog) {
            send(emitter, "log", line);
        }
        if (running.get()) {
            emitters.add(emitter);
            emitter.onCompletion(() -> emitters.remove(emitter));
            emitter.onTimeout(() -> emitters.remove(emitter));
        } else {
            // No active run: tell the client the last known outcome and close.
            RunStatus last = lastRun;
            send(emitter, "done", last == null ? "{\"status\":\"idle\"}" : statusJson(last));
            emitter.complete();
        }
    }

    private void execute() {
        String startedAt = lastRun.startedAt();
        int exit = -1;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    paths.python().toString(),
                    "-m", "ranker.rank",
                    "--candidates", paths.candidatesJsonl().toString(),
                    "--artifacts-dir", paths.artifactsDir().toString(),
                    "--out", paths.submissionCsv().toString());
            pb.directory(paths.repoRoot().toFile());
            pb.environment().put("KMP_DUPLICATE_LIB_OK", "TRUE");
            pb.redirectErrorStream(true);

            emit("log", "$ " + String.join(" ", pb.command()));
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    emit("log", line);
                }
            }
            exit = p.waitFor();

            if (exit == 0) {
                emit("log", "↻ Reloading results and profiles…");
                results.reload();
                profiles.reload();
                emit("log", "✓ Reloaded " + results.count() + " ranked candidates");
            } else {
                emit("log", "✗ Ranker exited with code " + exit);
            }
        } catch (Exception e) {
            log.error("Ranker run failed", e);
            emit("error", e.getMessage());
        } finally {
            String status = exit == 0 ? "succeeded" : "failed";
            lastRun = new RunStatus(runId, status, startedAt, Instant.now().toString(), exit);
            broadcast("done", statusJson(lastRun));
            running.set(false);
            for (SseEmitter e : emitters) {
                try {
                    e.complete();
                } catch (Exception ignored) {
                    // client already gone
                }
            }
            emitters.clear();
        }
    }

    private void emit(String event, String data) {
        if ("log".equals(event)) {
            currentLog.add(data);
        }
        broadcast(event, data);
    }

    private void broadcast(String event, String data) {
        for (SseEmitter e : emitters) {
            send(e, event, data);
        }
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }

    private static String statusJson(RunStatus s) {
        return "{\"runId\":\"" + s.runId() + "\",\"status\":\"" + s.status()
                + "\",\"exitCode\":" + s.exitCode() + "}";
    }

    /** Snapshot for the status endpoint. */
    public List<String> logSnapshot() {
        return new ArrayList<>(currentLog);
    }
}
