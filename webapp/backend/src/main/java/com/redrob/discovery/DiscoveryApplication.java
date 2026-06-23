package com.redrob.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Redrob Intelligent Candidate Discovery — demo web app.
 *
 * <p>This Spring Boot service is a thin, read-only consumer of the frozen Python ranking engine.
 * It serves the engine's {@code submission.csv} output joined with full candidate profiles from
 * {@code dataset/candidates.jsonl}, exposes the parsed job description, and can trigger a live
 * re-run of the Python ranker (streaming its logs over SSE). It never modifies the ranker.
 */
@SpringBootApplication
public class DiscoveryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiscoveryApplication.class, args);
    }
}
