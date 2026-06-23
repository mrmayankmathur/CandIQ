package com.redrob.discovery.model;

import java.util.List;

/** Ranking + match DTOs returned by the API. */
public final class Ranking {

    /** One row of the engine's submission.csv. */
    public record RankedResult(int rank, String candidateId, double score, String reasoning) {}

    /** Compact profile fields shown on a ranked card. */
    public record ProfileSummary(
            String candidateId,
            String name,
            String headline,
            String currentTitle,
            String currentCompany,
            String currentIndustry,
            double yearsOfExperience,
            String location,
            String country,
            List<String> topSkills,
            boolean openToWork,
            int noticePeriodDays,
            double recruiterResponseRate,
            String lastActiveDate) {}

    /** A ranked card: engine ranking + compact profile. */
    public record RankedCandidate(RankedResult ranking, ProfileSummary summary) {}

    /** One illustrative match bar (0..1). */
    public record MatchFactor(String label, double value, String detail) {}

    /**
     * Display-only "how well does this candidate fit" breakdown computed on the Java side.
     * The authoritative number is always the engine's {@link RankedResult#score()}.
     */
    public record MatchBreakdown(double overall, List<MatchFactor> factors, List<String> matchedSkills,
                                 String note) {}

    /** Full detail payload for one candidate. */
    public record CandidateDetail(RankedResult ranking, Candidate.CandidateProfile profile,
                                  MatchBreakdown match) {}

    /** State of a (current or last) live ranker re-run. */
    public record RunStatus(String runId, String status, String startedAt, String finishedAt,
                            Integer exitCode) {}

    /** Top-level service status. */
    public record StatusResponse(String resultsLoadedAt, int count, String repoRoot, boolean running,
                                 RunStatus lastRun) {}

    private Ranking() {}
}
