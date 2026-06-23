package com.redrob.discovery.model;

import java.util.List;

/** Curated, UI-facing view of the job description (parsed from {@code ranker/artifacts/jd_intent.json}). */
public final class Jd {

    public record SkillGroup(String category, String description, List<String> keywords, double weight) {}

    public record ExperienceBand(int statedMin, int statedMax, int idealMin, int idealMax, String note) {}

    public record JdLocation(List<String> preferred, List<String> acceptable, String workMode,
                             int noticePeriodIdealDays) {}

    public record Disqualifier(String id, String description, String severity) {}

    public record JobDescription(
            String jobTitle,
            String company,
            String summary,
            ExperienceBand experience,
            JdLocation location,
            List<SkillGroup> mustHave,
            List<SkillGroup> niceToHave,
            List<Disqualifier> disqualifiers,
            List<String> coreDomainTerms) {}

    private Jd() {}
}
