package com.redrob.discovery.web;

import com.redrob.discovery.service.RunnerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/** Triggers and streams a live re-run of the Python ranker. */
@RestController
@RequestMapping("/api/rank")
public class RankRunController {

    private final RunnerService runner;

    public RankRunController(RunnerService runner) {
        this.runner = runner;
    }

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> run() {
        if (runner.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("status", "already_running"));
        }
        String runId = runner.start();
        return ResponseEntity.ok(Map.of("runId", runId, "status", "started"));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        // ~10 min ceiling — comfortably longer than a ~60-90s run.
        SseEmitter emitter = new SseEmitter(600_000L);
        runner.subscribe(emitter);
        return emitter;
    }
}
