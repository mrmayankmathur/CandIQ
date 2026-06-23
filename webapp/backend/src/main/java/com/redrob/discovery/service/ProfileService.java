package com.redrob.discovery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redrob.discovery.config.AppPaths;
import com.redrob.discovery.model.Candidate.CandidateProfile;
import com.redrob.discovery.model.Candidate.Skill;
import com.redrob.discovery.model.Ranking.ProfileSummary;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the full profiles of just the ranked candidates. At startup (and after each live re-run)
 * it makes a single streaming pass over the ~100K-line {@code candidates.jsonl}, keeping only the
 * ~100 profiles that appear in {@code submission.csv}. Memory stays in the low megabytes.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final AppPaths paths;
    private final ResultsService results;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, CandidateProfile> profiles = new ConcurrentHashMap<>();

    public ProfileService(AppPaths paths, ResultsService results) {
        this.paths = paths;
        this.results = results;
    }

    @PostConstruct
    public void reload() {
        Set<String> wanted = new HashSet<>(results.orderedIds());
        if (wanted.isEmpty()) {
            log.warn("No ranked ids known; skipping profile load");
            return;
        }
        Map<String, CandidateProfile> loaded = new ConcurrentHashMap<>();
        long start = System.currentTimeMillis();
        long lines = 0;
        try (BufferedReader reader = Files.newBufferedReader(paths.candidatesJsonl(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && loaded.size() < wanted.size()) {
                if (line.isBlank()) {
                    continue;
                }
                lines++;
                // Cheap pre-filter: only parse JSON for lines that mention a wanted id.
                int idx = line.indexOf("\"candidate_id\"");
                if (idx < 0) {
                    continue;
                }
                JsonNode node = mapper.readTree(line);
                String id = node.path("candidate_id").asText();
                if (wanted.contains(id)) {
                    loaded.put(id, CandidateMapper.map(node));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read candidates.jsonl at " + paths.candidatesJsonl(), e);
        }
        profiles.clear();
        profiles.putAll(loaded);
        log.info("Cached {}/{} ranked profiles after scanning {} lines in {} ms",
                loaded.size(), wanted.size(), lines, System.currentTimeMillis() - start);
        if (loaded.size() < wanted.size()) {
            log.warn("{} ranked ids had no matching profile in candidates.jsonl", wanted.size() - loaded.size());
        }
    }

    public CandidateProfile get(String candidateId) {
        return profiles.get(candidateId);
    }

    /** Build the compact summary shown on a ranked card. */
    public ProfileSummary summary(CandidateProfile c) {
        var p = c.profile();
        var s = c.signals();
        return new ProfileSummary(
                c.candidateId(),
                p.anonymizedName(),
                p.headline(),
                p.currentTitle(),
                p.currentCompany(),
                p.currentIndustry(),
                p.yearsOfExperience(),
                p.location(),
                p.country(),
                topSkills(c.skills(), 6),
                s != null && s.openToWork(),
                s != null ? s.noticePeriodDays() : 0,
                s != null ? s.recruiterResponseRate() : 0.0,
                s != null ? s.lastActiveDate() : null);
    }

    private static List<String> topSkills(List<Skill> skills, int n) {
        List<String> out = new ArrayList<>();
        if (skills == null) {
            return out;
        }
        skills.stream()
                .sorted((a, b) -> Integer.compare(b.endorsements(), a.endorsements()))
                .limit(n)
                .forEach(sk -> out.add(sk.name()));
        return out;
    }
}
