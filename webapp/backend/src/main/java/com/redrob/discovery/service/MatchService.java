package com.redrob.discovery.service;

import com.redrob.discovery.model.Candidate.CandidateProfile;
import com.redrob.discovery.model.Jd.JobDescription;
import com.redrob.discovery.model.Jd.SkillGroup;
import com.redrob.discovery.model.Ranking.MatchBreakdown;
import com.redrob.discovery.model.Ranking.MatchFactor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Computes an illustrative "fit" breakdown for the UI: skill coverage, experience/location fit, and
 * job-market intent. This is purely for visualization — the authoritative ranking number is always
 * the frozen engine's composite score. Nothing here feeds back into ranking.
 */
@Service
public class MatchService {

    private static final String NOTE =
            "Illustrative match indicators derived from the profile for display only. "
                    + "The authoritative ranking is the engine's composite score.";

    public MatchBreakdown compute(CandidateProfile c, JobDescription jd) {
        String corpus = corpus(c);

        // 1. Must-have skill coverage: fraction of must-have categories with >=1 keyword hit.
        int total = jd.mustHave().size();
        int covered = 0;
        for (SkillGroup g : jd.mustHave()) {
            if (anyKeyword(corpus, g.keywords())) {
                covered++;
            }
        }
        double coverage = total == 0 ? 0 : (double) covered / total;

        // 2. Experience fit vs the ideal band.
        double yoe = c.profile().yearsOfExperience();
        int lo = jd.experience().idealMin();
        int hi = jd.experience().idealMax();
        double expFit;
        if (yoe >= lo && yoe <= hi) {
            expFit = 1.0;
        } else {
            double dist = yoe < lo ? lo - yoe : yoe - hi;
            expFit = Math.max(0.0, 1.0 - dist / 5.0);
        }

        // 3. Location fit.
        double locFit = locationFit(c, jd);

        // 4. Job-market intent (Redrob signals).
        double intent = intent(c);

        List<MatchFactor> factors = List.of(
                new MatchFactor("Must-have skill coverage", coverage,
                        covered + "/" + total + " required skill areas matched"),
                new MatchFactor("Experience fit", expFit,
                        fmt(yoe) + " yrs vs ideal " + lo + "-" + hi + " yrs"),
                new MatchFactor("Location fit", locFit, locationDetail(c, jd)),
                new MatchFactor("Job-market intent", intent, intentDetail(c)));

        double overall = factors.stream().mapToDouble(MatchFactor::value).average().orElse(0.0);

        return new MatchBreakdown(overall, factors, matchedSkills(corpus, jd), NOTE);
    }

    private static String corpus(CandidateProfile c) {
        StringBuilder sb = new StringBuilder();
        var p = c.profile();
        append(sb, p.headline());
        append(sb, p.summary());
        append(sb, p.currentTitle());
        append(sb, p.currentIndustry());
        if (c.skills() != null) {
            c.skills().forEach(s -> append(sb, s.name()));
        }
        if (c.careerHistory() != null) {
            c.careerHistory().forEach(j -> {
                append(sb, j.title());
                append(sb, j.description());
            });
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder sb, String s) {
        if (s != null) {
            sb.append(' ').append(s);
        }
    }

    private static boolean anyKeyword(String corpus, List<String> keywords) {
        for (String k : keywords) {
            if (corpus.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private double locationFit(CandidateProfile c, JobDescription jd) {
        String loc = lower(c.profile().location());
        String country = lower(c.profile().country());
        for (String pref : jd.location().preferred()) {
            if (contains(loc, pref)) {
                return 1.0;
            }
        }
        for (String acc : jd.location().acceptable()) {
            if (contains(loc, acc) || contains(country, acc)) {
                return 0.75;
            }
        }
        if (contains(country, "india")) {
            return 0.6;
        }
        return 0.3;
    }

    private String locationDetail(CandidateProfile c, JobDescription jd) {
        String loc = c.profile().location();
        for (String pref : jd.location().preferred()) {
            if (contains(lower(loc), pref)) {
                return loc + " — preferred location";
            }
        }
        return (loc == null ? "Unknown" : loc) + " (" + c.profile().country() + ")";
    }

    private double intent(CandidateProfile c) {
        var s = c.signals();
        if (s == null) {
            return 0.0;
        }
        double openToWork = s.openToWork() ? 1.0 : 0.3;
        double response = clamp(s.recruiterResponseRate());
        double notice = s.noticePeriodDays() <= 30 ? 1.0
                : s.noticePeriodDays() <= 60 ? 0.7 : 0.4;
        return (openToWork + response + notice) / 3.0;
    }

    private String intentDetail(CandidateProfile c) {
        var s = c.signals();
        if (s == null) {
            return "No signals";
        }
        return (s.openToWork() ? "Open to work" : "Passive")
                + ", response rate " + fmt(s.recruiterResponseRate())
                + ", notice " + s.noticePeriodDays() + "d";
    }

    /** Candidate skill names that hit any JD keyword (must/nice/core) — for the matched-skill chips. */
    private List<String> matchedSkills(String corpus, JobDescription jd) {
        Set<String> keywords = new LinkedHashSet<>();
        jd.mustHave().forEach(g -> keywords.addAll(g.keywords()));
        jd.niceToHave().forEach(g -> keywords.addAll(g.keywords()));
        keywords.addAll(jd.coreDomainTerms());

        List<String> matched = new ArrayList<>();
        for (String k : keywords) {
            if (corpus.contains(k.toLowerCase(Locale.ROOT))) {
                matched.add(k);
            }
            if (matched.size() >= 10) {
                break;
            }
        }
        return matched;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && needle != null
                && haystack.contains(needle.toLowerCase(Locale.ROOT));
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
