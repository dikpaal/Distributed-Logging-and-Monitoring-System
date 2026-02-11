package com.logging.user.controller;

import com.logging.user.service.LogGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/generator")
public class GeneratorController {

    private final LogGeneratorService generatorService;

    public GeneratorController(LogGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        generatorService.start();
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Log generator is now running"
        ));
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        generatorService.stop();
        return ResponseEntity.ok(Map.of(
                "status", "stopped",
                "message", "Log generator has been stopped"
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "running", generatorService.isRunning(),
                "successCount", generatorService.getSuccessCount(),
                "errorCount", generatorService.getErrorCount()
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        generatorService.resetCounters();
        return ResponseEntity.ok(Map.of(
                "status", "reset",
                "message", "Counters have been reset"
        ));
    }
}
