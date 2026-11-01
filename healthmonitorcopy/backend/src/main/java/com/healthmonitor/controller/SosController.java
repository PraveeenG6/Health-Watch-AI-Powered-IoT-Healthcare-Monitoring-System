package com.healthmonitor.controller;

import com.healthmonitor.dto.SosRequest;
import com.healthmonitor.model.Alert;
import com.healthmonitor.repository.AlertRepository;
import com.healthmonitor.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/sos")
@RequiredArgsConstructor
public class SosController {

    private final AlertRepository alertRepository;
    private final AuthService authService;

    @PostMapping
    public Map<String, Object> triggerSos(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody SosRequest request) {
        authService.requirePatientOrDoctorForPatient(authorization, request.getPatientId());

        Alert alert = Alert.builder()
                .patientId(request.getPatientId())
                .alertType("SOS")
                .message(request.getMessage() == null || request.getMessage().isBlank()
                        ? "SOS emergency alert"
                        : request.getMessage())
                .severity("CRITICAL")
                .active(true)
                .triggeredAt(Instant.now())
                .build();

        alert = alertRepository.save(alert);
        return Map.of(
                "status", "SOS_SENT",
                "alertId", alert.getId(),
                "message", "Emergency alert saved"
        );
    }
}
