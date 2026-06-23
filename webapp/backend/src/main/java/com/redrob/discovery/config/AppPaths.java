package com.redrob.discovery.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the locations of the frozen ranker's inputs/outputs.
 *
 * <p>Resolution order for the repo root:
 * <ol>
 *   <li>{@code discovery.repo-root} property / {@code DISCOVERY_REPO_ROOT} env (if it exists)</li>
 *   <li>walk up from the working directory looking for a folder that contains both
 *       {@code submission.csv} and {@code ranker/}</li>
 *   <li>a hardcoded fallback to this machine's checkout</li>
 * </ol>
 * Every derived path stays overridable so the same jar runs unchanged inside Docker.
 */
@Component
public class AppPaths {

    private static final Logger log = LoggerFactory.getLogger(AppPaths.class);
    private static final String FALLBACK_ROOT =
            "/Users/mayankmathur/Desktop/Mayanks_Projects/intelligent_candidate_discovery";

    private final String configuredRoot;
    private final String configuredPython;

    private Path repoRoot;

    public AppPaths(
            @Value("${discovery.repo-root:}") String configuredRoot,
            @Value("${discovery.python:}") String configuredPython) {
        this.configuredRoot = configuredRoot;
        this.configuredPython = configuredPython;
    }

    @PostConstruct
    void init() {
        this.repoRoot = resolveRepoRoot();
        log.info("Repo root resolved to {}", repoRoot);
        log.info("  submission.csv  -> {}", submissionCsv());
        log.info("  candidates      -> {}", candidatesJsonl());
        log.info("  jd_intent.json  -> {}", jdIntentJson());
        log.info("  python          -> {}", python());
    }

    private Path resolveRepoRoot() {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            Path p = Paths.get(configuredRoot).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p;
            }
            log.warn("Configured discovery.repo-root '{}' not found, falling back to auto-detect", configuredRoot);
        }
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.exists(dir.resolve("submission.csv")) && Files.isDirectory(dir.resolve("ranker"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        log.warn("Could not auto-detect repo root from working dir; using fallback {}", FALLBACK_ROOT);
        return Paths.get(FALLBACK_ROOT).toAbsolutePath().normalize();
    }

    public Path repoRoot() {
        return repoRoot;
    }

    public Path submissionCsv() {
        return repoRoot.resolve("submission.csv");
    }

    public Path candidatesJsonl() {
        return repoRoot.resolve("dataset").resolve("candidates.jsonl");
    }

    public Path artifactsDir() {
        return repoRoot.resolve("ranker").resolve("artifacts");
    }

    public Path jdIntentJson() {
        return artifactsDir().resolve("jd_intent.json");
    }

    public Path python() {
        if (configuredPython != null && !configuredPython.isBlank()) {
            return Paths.get(configuredPython);
        }
        return repoRoot.resolve(".venv").resolve("bin").resolve("python");
    }
}
