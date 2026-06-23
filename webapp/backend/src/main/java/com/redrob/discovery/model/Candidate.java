package com.redrob.discovery.model;

import java.util.List;

/**
 * Clean, camelCase view of a candidate profile (subset of the dataset schema that the UI renders).
 * Populated by {@link com.redrob.discovery.service.CandidateMapper} from the raw JSONL node so that
 * the dataset's snake_case keys never leak into the public API.
 */
public final class Candidate {

    public record Profile(
            String anonymizedName,
            String headline,
            String summary,
            String location,
            String country,
            double yearsOfExperience,
            String currentTitle,
            String currentCompany,
            String currentCompanySize,
            String currentIndustry) {}

    public record Job(
            String company,
            String title,
            String startDate,
            String endDate,
            int durationMonths,
            boolean current,
            String industry,
            String companySize,
            String description) {}

    public record Education(
            String institution,
            String degree,
            String fieldOfStudy,
            int startYear,
            int endYear,
            String grade,
            String tier) {}

    public record Skill(
            String name,
            String proficiency,
            int endorsements,
            Integer durationMonths) {}

    public record Certification(String name, String issuer, int year) {}

    public record Language(String language, String proficiency) {}

    public record SalaryRange(double min, double max) {}

    public record Signals(
            double profileCompletenessScore,
            String signupDate,
            String lastActiveDate,
            boolean openToWork,
            int profileViewsReceived30d,
            int applicationsSubmitted30d,
            double recruiterResponseRate,
            double avgResponseTimeHours,
            int connectionCount,
            int endorsementsReceived,
            int noticePeriodDays,
            SalaryRange expectedSalaryLpa,
            String preferredWorkMode,
            boolean willingToRelocate,
            double githubActivityScore,
            int searchAppearance30d,
            int savedByRecruiters30d,
            double interviewCompletionRate,
            double offerAcceptanceRate,
            boolean verifiedEmail,
            boolean verifiedPhone,
            boolean linkedinConnected) {}

    public record CandidateProfile(
            String candidateId,
            Profile profile,
            List<Job> careerHistory,
            List<Education> education,
            List<Skill> skills,
            List<Certification> certifications,
            List<Language> languages,
            Signals signals) {}

    private Candidate() {}
}
