package com.redrob.discovery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.redrob.discovery.model.Candidate;
import com.redrob.discovery.model.Candidate.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-maps a raw candidate JSONL node (snake_case dataset schema) into the clean camelCase
 * {@link CandidateProfile}. Manual mapping avoids Jackson naming-strategy edge cases (e.g. the
 * {@code _30d} suffixes) and keeps the public API free of the dataset's internal key names.
 */
final class CandidateMapper {

    static CandidateProfile map(JsonNode n) {
        JsonNode p = n.path("profile");
        Profile profile = new Profile(
                text(p, "anonymized_name"),
                text(p, "headline"),
                text(p, "summary"),
                text(p, "location"),
                text(p, "country"),
                dbl(p, "years_of_experience"),
                text(p, "current_title"),
                text(p, "current_company"),
                text(p, "current_company_size"),
                text(p, "current_industry"));

        List<Job> career = new ArrayList<>();
        for (JsonNode j : array(n, "career_history")) {
            career.add(new Job(
                    text(j, "company"),
                    text(j, "title"),
                    text(j, "start_date"),
                    j.hasNonNull("end_date") ? j.get("end_date").asText() : null,
                    intg(j, "duration_months"),
                    bool(j, "is_current"),
                    text(j, "industry"),
                    text(j, "company_size"),
                    text(j, "description")));
        }

        List<Education> education = new ArrayList<>();
        for (JsonNode e : array(n, "education")) {
            education.add(new Education(
                    text(e, "institution"),
                    text(e, "degree"),
                    text(e, "field_of_study"),
                    intg(e, "start_year"),
                    intg(e, "end_year"),
                    e.hasNonNull("grade") ? e.get("grade").asText() : null,
                    text(e, "tier")));
        }

        List<Skill> skills = new ArrayList<>();
        for (JsonNode s : array(n, "skills")) {
            skills.add(new Skill(
                    text(s, "name"),
                    text(s, "proficiency"),
                    intg(s, "endorsements"),
                    s.hasNonNull("duration_months") ? s.get("duration_months").asInt() : null));
        }

        List<Certification> certs = new ArrayList<>();
        for (JsonNode c : array(n, "certifications")) {
            certs.add(new Certification(text(c, "name"), text(c, "issuer"), intg(c, "year")));
        }

        List<Language> languages = new ArrayList<>();
        for (JsonNode l : array(n, "languages")) {
            languages.add(new Language(text(l, "language"), text(l, "proficiency")));
        }

        Signals signals = mapSignals(n.path("redrob_signals"));

        return new CandidateProfile(
                text(n, "candidate_id"), profile, career, education, skills, certs, languages, signals);
    }

    private static Signals mapSignals(JsonNode s) {
        JsonNode salary = s.path("expected_salary_range_inr_lpa");
        SalaryRange range = new SalaryRange(dbl(salary, "min"), dbl(salary, "max"));
        return new Signals(
                dbl(s, "profile_completeness_score"),
                text(s, "signup_date"),
                text(s, "last_active_date"),
                bool(s, "open_to_work_flag"),
                intg(s, "profile_views_received_30d"),
                intg(s, "applications_submitted_30d"),
                dbl(s, "recruiter_response_rate"),
                dbl(s, "avg_response_time_hours"),
                intg(s, "connection_count"),
                intg(s, "endorsements_received"),
                intg(s, "notice_period_days"),
                range,
                text(s, "preferred_work_mode"),
                bool(s, "willing_to_relocate"),
                dbl(s, "github_activity_score"),
                intg(s, "search_appearance_30d"),
                intg(s, "saved_by_recruiters_30d"),
                dbl(s, "interview_completion_rate"),
                dbl(s, "offer_acceptance_rate"),
                bool(s, "verified_email"),
                bool(s, "verified_phone"),
                bool(s, "linkedin_connected"));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static double dbl(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? 0.0 : v.asDouble();
    }

    private static int intg(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? 0 : v.asInt();
    }

    private static boolean bool(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && !v.isNull() && v.asBoolean();
    }

    private static Iterable<JsonNode> array(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && v.isArray() ? v : List.of();
    }

    private CandidateMapper() {}
}
