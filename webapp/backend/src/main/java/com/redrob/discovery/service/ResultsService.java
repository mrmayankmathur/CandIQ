package com.redrob.discovery.service;

import com.redrob.discovery.config.AppPaths;
import com.redrob.discovery.model.Ranking.RankedResult;
import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads and holds the engine's {@code submission.csv} (top-100 ranked rows) in memory. */
@Service
public class ResultsService {

    private static final Logger log = LoggerFactory.getLogger(ResultsService.class);

    private final AppPaths paths;

    private volatile List<RankedResult> ordered = List.of();
    private volatile Map<String, RankedResult> byId = Map.of();
    private volatile Instant loadedAt;

    public ResultsService(AppPaths paths) {
        this.paths = paths;
    }

    @PostConstruct
    public synchronized void reload() {
        List<RankedResult> rows = new ArrayList<>();
        Map<String, RankedResult> index = new LinkedHashMap<>();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();
        try (Reader reader = Files.newBufferedReader(paths.submissionCsv(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord rec : parser) {
                RankedResult r = new RankedResult(
                        Integer.parseInt(rec.get("rank").trim()),
                        rec.get("candidate_id").trim(),
                        Double.parseDouble(rec.get("score").trim()),
                        rec.get("reasoning"));
                rows.add(r);
                index.put(r.candidateId(), r);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read submission.csv at " + paths.submissionCsv(), e);
        }
        this.ordered = List.copyOf(rows);
        this.byId = Map.copyOf(index);
        this.loadedAt = Instant.now();
        log.info("Loaded {} ranked results from {}", rows.size(), paths.submissionCsv());
    }

    public List<RankedResult> all() {
        return ordered;
    }

    public List<RankedResult> top(int limit) {
        return ordered.subList(0, Math.min(limit, ordered.size()));
    }

    public RankedResult byId(String candidateId) {
        return byId.get(candidateId);
    }

    /** Ordered candidate ids, used by {@link ProfileService} to know which profiles to cache. */
    public List<String> orderedIds() {
        List<String> ids = new ArrayList<>(ordered.size());
        for (RankedResult r : ordered) {
            ids.add(r.candidateId());
        }
        return ids;
    }

    public int count() {
        return ordered.size();
    }

    public Instant loadedAt() {
        return loadedAt;
    }
}
