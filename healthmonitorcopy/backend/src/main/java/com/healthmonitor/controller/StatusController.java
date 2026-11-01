package com.healthmonitor.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StatusController {

    private final MongoTemplate mongoTemplate;

    @GetMapping({"/", "/api/ping"})
    public Map<String, String> status() {
        return Map.of(
                "service", "healthwatch-backend",
                "status", "ok"
        );
    }

    @GetMapping("/api/status")
    public Map<String, Object> fullStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "healthwatch-backend");
        status.put("status", "ok");
        status.put("timestamp", Instant.now().toString());

        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            status.put("mongo", "connected");
            status.put("database", mongoTemplate.getDb().getName());
        } catch (Exception ex) {
            status.put("mongo", "disconnected");
            status.put("mongoError", ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }

        return status;
    }
}
