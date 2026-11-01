package com.healthmonitor.controller;

import com.healthmonitor.service.AuthService;
import com.healthmonitor.service.ai.HealthAiService;
import com.healthmonitor.service.ai.HealthAiService.HealthAiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final HealthAiService healthAiService;
    private final AuthService authService;

    @GetMapping("/analyze")
    public HealthAiResult analyzeVitals(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "0") double xAccel,
            @RequestParam(defaultValue = "0") double yAccel,
            @RequestParam(defaultValue = "1") double zAccel,
            @RequestParam double heartRate,
            @RequestParam double spo2,
            @RequestParam double temp) {
        authService.authenticate(authorization);
        return healthAiService.analyze(xAccel, yAccel, zAccel, heartRate, spo2, temp);
    }
}
