package com.redrob.discovery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redrob.discovery.model.Candidate.CandidateProfile;
import com.redrob.discovery.model.Candidate.Job;
import com.redrob.discovery.model.Candidate.Signals;
import com.redrob.discovery.model.Candidate.Skill;
import com.redrob.discovery.model.Jd.JobDescription;
import com.redrob.discovery.model.Jd.SkillGroup;
import com.redrob.discovery.model.Ranking.RankedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Streams a grounded, recruiter-grade assessment of one ranked candidate from OpenAI.
 *
 * <p>This is a <b>demo-only</b> feature of the web app. The judged ranking engine ({@code ranker/})
 * never calls any LLM and stays fully offline — this class lives entirely on the Java side and only
 * reads data the app already holds (profile + JD intent + the engine's own score/reasoning).
 *
 * <p>With no {@code OPENAI_API_KEY} configured, {@link #isEnabled()} is {@code false} and callers skip
 * the network entirely, so the rest of the app is unaffected.
 *
 * <p>Completed assessments are memoised per candidate with a single-flight cache so re-opening a
 * profile (or two clients opening it at once) reuses one OpenAI call instead of billing twice.
 */
@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private static final String SYSTEM_PROMPT = """
            You are a senior technical recruiter evaluating one candidate against a specific job description.
            Assess ONLY using the data provided in the user message — never invent employers, skills, dates,
            or numbers. If the data is insufficient for a judgement, say so plainly.

            Respond in GitHub-flavored markdown using EXACTLY these four section headers, in this order:
            ## Verdict
            ## Strengths
            ## Risks & red flags
            ## Interview questions

            Be concise and concrete, and cite specifics from the candidate's data (companies, skills, signals).""";

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;
    private final String chatCompletionsUrl;
    private final long requestTimeoutMs;

    /** candidateId -> the full assessment text (single-flight: in-progress entries are shared, failures evicted). */
    private final ConcurrentHashMap<String, CompletableFuture<String>> cache = new ConcurrentHashMap<>();

    public LlmService(
            HttpClient openAiHttpClient,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-5}") String model,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.request-timeout-ms:60000}") long requestTimeoutMs) {
        this.http = openAiHttpClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.chatCompletionsUrl = stripTrailingSlash(baseUrl) + "/chat/completions";
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /** True when an API key is configured; callers should show a friendly hint otherwise. */
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /**
     * Stream a candidate assessment, delivering each token to {@code onToken} as it arrives.
     * Blocks until the assessment is complete. Safe to call concurrently for the same candidate:
     * the first caller drives the OpenAI stream, later callers await and replay the finished text.
     *
     * @throws Exception on any network/parse failure (the controller surfaces it as an {@code error} event)
     */
    public void streamAssessment(String candidateId, CandidateProfile profile, JobDescription jd,
                                 RankedResult result, Consumer<String> onToken) throws Exception {
        CompletableFuture<String> existing = cache.get(candidateId);
        if (existing != null) {
            onToken.accept(existing.join());   // replay (CompletionException only if a prior run failed — those are evicted)
            return;
        }
        CompletableFuture<String> mine = new CompletableFuture<>();
        CompletableFuture<String> prior = cache.putIfAbsent(candidateId, mine);
        if (prior != null) {
            onToken.accept(prior.join());
            return;
        }
        // We own this candidate's assessment: drive the live stream.
        try {
            String full = callOpenAiStreaming(profile, jd, result, onToken);
            mine.complete(full);
        } catch (Exception e) {
            mine.completeExceptionally(e);
            cache.remove(candidateId, mine);   // allow a later retry
            throw e;
        }
    }

    private String callOpenAiStreaming(CandidateProfile profile, JobDescription jd, RankedResult result,
                                       Consumer<String> onToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(chatCompletionsUrl))
                .timeout(Duration.ofMillis(requestTimeoutMs))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(profile, jd, result), StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.util.stream.Stream<String>> resp = http.send(req, HttpResponse.BodyHandlers.ofLines());
        if (resp.statusCode() / 100 != 2) {
            // Errors come back as a normal (non-SSE) JSON body; join the lines for a readable message.
            String err;
            try (var body = resp.body()) {
                err = body.collect(Collectors.joining("\n"));
            }
            throw new IllegalStateException("OpenAI API returned " + resp.statusCode() + ": " + truncate(err, 300));
        }

        StringBuilder full = new StringBuilder();
        try (var body = resp.body()) {
            Iterator<String> it = body.iterator();
            while (it.hasNext()) {
                String line = it.next().trim();
                if (!line.startsWith("data:")) {
                    continue;                       // skip blank separators and ":" keep-alive pings
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.equals("[DONE]")) {
                    break;
                }
                String token = extractDelta(payload);
                if (token != null && !token.isEmpty()) {
                    full.append(token);
                    onToken.accept(token);
                }
            }
        }
        if (full.length() == 0) {
            throw new IllegalStateException("OpenAI stream produced no content");
        }
        return full.toString();
    }

    /** Read {@code choices[0].delta.content} from one SSE data payload; null if absent/unparseable. */
    private String extractDelta(String payload) {
        try {
            JsonNode content = mapper.readTree(payload).path("choices").path(0).path("delta").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            log.debug("Skipping unparseable SSE payload: {}", truncate(payload, 120));
            return null;
        }
    }

    private String buildRequestBody(CandidateProfile profile, JobDescription jd, RankedResult result) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("stream", true);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", buildUserContent(profile, jd, result));
        return mapper.writeValueAsString(root);
    }

    /** A compact, grounded brief: the JD intent + this candidate's real data + the engine's verdict. */
    private String buildUserContent(CandidateProfile c, JobDescription jd, RankedResult r) {
        StringBuilder b = new StringBuilder();

        b.append("# JOB DESCRIPTION\n");
        if (jd != null) {
            appendLine(b, "Title", jd.jobTitle());
            appendLine(b, "Summary", jd.summary());
            if (jd.experience() != null) {
                appendLine(b, "Experience band",
                        jd.experience().idealMin() + "-" + jd.experience().idealMax() + " yrs (ideal)");
            }
            if (jd.mustHave() != null && !jd.mustHave().isEmpty()) {
                b.append("Must-have skills:\n");
                for (SkillGroup g : jd.mustHave()) {
                    b.append("  - ").append(g.category());
                    if (g.keywords() != null && !g.keywords().isEmpty()) {
                        b.append(": ").append(String.join(", ", g.keywords()));
                    }
                    b.append("\n");
                }
            }
            if (jd.disqualifiers() != null && !jd.disqualifiers().isEmpty()) {
                b.append("Disqualifiers:\n");
                jd.disqualifiers().forEach(d -> b.append("  - ").append(d.description()).append("\n"));
            }
        }

        b.append("\n# CANDIDATE\n");
        var p = c.profile();
        if (p != null) {
            appendLine(b, "Name", p.anonymizedName());
            appendLine(b, "Headline", p.headline());
            appendLine(b, "Experience", p.yearsOfExperience() + " yrs");
            appendLine(b, "Current", join(" @ ", p.currentTitle(), p.currentCompany())
                    + (p.currentIndustry() != null ? " (" + p.currentIndustry() + ")" : ""));
            appendLine(b, "Location", join(", ", p.location(), p.country()));
            appendLine(b, "Summary", p.summary());
        }

        List<Job> jobs = c.careerHistory();
        if (jobs != null && !jobs.isEmpty()) {
            b.append("Career history:\n");
            for (Job j : jobs) {
                b.append("  - ").append(join(" @ ", j.title(), j.company()));
                b.append(" (").append(nullToDash(j.startDate())).append(" – ")
                        .append(j.endDate() == null ? "present" : j.endDate())
                        .append(", ").append(j.durationMonths()).append(" mo");
                if (j.industry() != null) {
                    b.append(", ").append(j.industry());
                }
                b.append(")\n");
            }
        }

        List<Skill> skills = c.skills();
        if (skills != null && !skills.isEmpty()) {
            String top = skills.stream()
                    .sorted((a, z) -> Integer.compare(z.endorsements(), a.endorsements()))
                    .limit(20)
                    .map(sk -> sk.proficiency() == null ? sk.name() : sk.name() + " (" + sk.proficiency() + ")")
                    .collect(Collectors.joining(", "));
            appendLine(b, "Top skills", top);
        }

        if (c.education() != null && !c.education().isEmpty()) {
            String edu = c.education().stream()
                    .map(e -> join(" ", e.degree(), e.fieldOfStudy()) + " — " + nullToDash(e.institution())
                            + " (" + e.endYear() + ")")
                    .collect(Collectors.joining("; "));
            appendLine(b, "Education", edu);
        }

        Signals s = c.signals();
        if (s != null) {
            b.append("Redrob signals: ")
                    .append("open_to_work=").append(s.openToWork())
                    .append(", notice_period_days=").append(s.noticePeriodDays())
                    .append(", recruiter_response_rate=").append(round2(s.recruiterResponseRate()))
                    .append(", last_active=").append(nullToDash(s.lastActiveDate()))
                    .append(", preferred_work_mode=").append(nullToDash(s.preferredWorkMode()))
                    .append(", willing_to_relocate=").append(s.willingToRelocate())
                    .append(", github_activity=").append((int) s.githubActivityScore())
                    .append("\n");
        }

        b.append("\n# RANKING ENGINE VERDICT\n");
        if (r != null) {
            appendLine(b, "Rank", "#" + r.rank() + " of 100");
            appendLine(b, "Score", round2(r.score()) + " (0–1)");
            appendLine(b, "Engine reasoning", r.reasoning());
        }
        return b.toString();
    }

    // ── small formatting helpers ────────────────────────────────────────────────────────────────

    private static void appendLine(StringBuilder b, String label, String value) {
        if (value != null && !value.isBlank()) {
            b.append(label).append(": ").append(value).append("\n");
        }
    }

    private static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (b.length() > 0) {
                    b.append(sep);
                }
                b.append(p);
            }
        }
        return b.toString();
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static String round2(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String stripTrailingSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }
}
