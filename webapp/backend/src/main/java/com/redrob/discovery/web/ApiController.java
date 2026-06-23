package com.redrob.discovery.web;

import com.redrob.discovery.config.AppPaths;
import com.redrob.discovery.model.Candidate.CandidateProfile;
import com.redrob.discovery.model.Jd.JobDescription;
import com.redrob.discovery.model.Ranking.CandidateDetail;
import com.redrob.discovery.model.Ranking.MatchBreakdown;
import com.redrob.discovery.model.Ranking.ProfileSummary;
import com.redrob.discovery.model.Ranking.RankedCandidate;
import com.redrob.discovery.model.Ranking.RankedResult;
import com.redrob.discovery.model.Ranking.StatusResponse;
import com.redrob.discovery.service.JdService;
import com.redrob.discovery.service.MatchService;
import com.redrob.discovery.service.ProfileService;
import com.redrob.discovery.service.ResultsService;
import com.redrob.discovery.service.RunnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Read-only API over the engine's output, the JD, and per-candidate match breakdowns. */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final AppPaths paths;
    private final JdService jdService;
    private final ResultsService results;
    private final ProfileService profiles;
    private final MatchService matchService;
    private final RunnerService runner;

    public ApiController(AppPaths paths, JdService jdService, ResultsService results,
                         ProfileService profiles, MatchService matchService, RunnerService runner) {
        this.paths = paths;
        this.jdService = jdService;
        this.results = results;
        this.profiles = profiles;
        this.matchService = matchService;
        this.runner = runner;
    }

    @GetMapping("/jd")
    public JobDescription jd() {
        return jdService.get();
    }

    @GetMapping("/results")
    public List<RankedCandidate> results(@RequestParam(defaultValue = "100") int limit) {
        List<RankedCandidate> out = new ArrayList<>();
        for (RankedResult r : results.top(limit)) {
            CandidateProfile profile = profiles.get(r.candidateId());
            ProfileSummary summary = profile != null ? profiles.summary(profile) : null;
            out.add(new RankedCandidate(r, summary));
        }
        return out;
    }

    @GetMapping("/candidates/{id}")
    public ResponseEntity<CandidateDetail> candidate(@PathVariable String id) {
        RankedResult ranking = results.byId(id);
        CandidateProfile profile = profiles.get(id);
        if (ranking == null || profile == null) {
            return ResponseEntity.notFound().build();
        }
        MatchBreakdown match = matchService.compute(profile, jdService.get());
        return ResponseEntity.ok(new CandidateDetail(ranking, profile, match));
    }

    @GetMapping("/status")
    public StatusResponse status() {
        Instant loadedAt = results.loadedAt();
        return new StatusResponse(
                loadedAt == null ? null : loadedAt.toString(),
                results.count(),
                paths.repoRoot().toString(),
                runner.isRunning(),
                runner.lastRun());
    }
}
