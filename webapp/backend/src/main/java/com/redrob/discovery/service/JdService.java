package com.redrob.discovery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redrob.discovery.config.AppPaths;
import com.redrob.discovery.model.Jd.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Parses the frozen JD intent file into a curated {@link JobDescription}. */
@Service
public class JdService {

    private static final Logger log = LoggerFactory.getLogger(JdService.class);

    private final AppPaths paths;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile JobDescription jd;

    public JdService(AppPaths paths) {
        this.paths = paths;
    }

    @PostConstruct
    void load() {
        try {
            JsonNode root = mapper.readTree(Files.readString(paths.jdIntentJson()));
            this.jd = parse(root);
            log.info("Loaded JD: '{}' ({} must-have, {} nice-to-have skill groups)",
                    jd.jobTitle(), jd.mustHave().size(), jd.niceToHave().size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read jd_intent.json at " + paths.jdIntentJson(), e);
        }
    }

    public JobDescription get() {
        return jd;
    }

    private JobDescription parse(JsonNode r) {
        JsonNode exp = r.path("experience_range");
        ExperienceBand band = new ExperienceBand(
                exp.path("stated_min").asInt(),
                exp.path("stated_max").asInt(),
                exp.path("ideal_min").asInt(),
                exp.path("ideal_max").asInt(),
                exp.path("note").asText(null));

        JsonNode loc = r.path("location");
        JdLocation location = new JdLocation(
                strings(loc.path("preferred")),
                strings(loc.path("acceptable")),
                loc.path("work_mode").asText(null),
                loc.path("notice_period_ideal_days").asInt());

        List<Disqualifier> dqs = new ArrayList<>();
        for (JsonNode d : iter(r.path("explicit_disqualifiers"))) {
            dqs.add(new Disqualifier(
                    d.path("id").asText(),
                    d.path("description").asText(),
                    d.path("severity").asText()));
        }

        return new JobDescription(
                r.path("job_title").asText(),
                r.path("company").asText(),
                r.path("summary").asText(),
                band,
                location,
                skillGroups(r.path("must_have_skills")),
                skillGroups(r.path("nice_to_have_skills")),
                dqs,
                strings(r.path("expanded_search_terms").path("core_domain")));
    }

    private List<SkillGroup> skillGroups(JsonNode arr) {
        List<SkillGroup> out = new ArrayList<>();
        for (JsonNode g : iter(arr)) {
            out.add(new SkillGroup(
                    g.path("category").asText(),
                    g.path("description").asText(null),
                    strings(g.path("keywords")),
                    g.path("weight").asDouble(0.0)));
        }
        return out;
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : iter(arr)) {
            out.add(n.asText());
        }
        return out;
    }

    private static Iterable<JsonNode> iter(JsonNode n) {
        return n != null && n.isArray() ? n : List.of();
    }
}
