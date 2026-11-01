package com.healthmonitor.controller;

import com.healthmonitor.dto.AlertResponse;
import com.healthmonitor.dto.HealthSummaryDTO;
import com.healthmonitor.dto.SensorDataRequest;
import com.healthmonitor.model.HealthReading;
import com.healthmonitor.service.AuthService;
import com.healthmonitor.service.HealthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;
    private final AuthService authService;

    @PostMapping("/reading")
    public AlertResponse receiveReading(@Valid @RequestBody SensorDataRequest request) {
        return healthService.processSensorData(request);
    }

    @GetMapping("/latest/{patientId}")
    public HealthReading getLatest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String patientId) {
        authService.requirePatientOrDoctorForPatient(authorization, patientId);
        HealthReading reading = healthService.getLatestReading(patientId);
        if (reading == null) {
            throw new RuntimeException("Reading not found for patient: " + patientId);
        }
        return reading;
    }

    @GetMapping("/history/{patientId}")
    public Page<HealthReading> getHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        authService.requirePatientOrDoctorForPatient(authorization, patientId);
        return healthService.getHistory(
                patientId, PageRequest.of(page, size, Sort.by("recordedAt").descending()));
    }

    @GetMapping("/range/{patientId}")
    public List<HealthReading> getRangeReadings(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String patientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        authService.requirePatientOrDoctorForPatient(authorization, patientId);
        Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return healthService.getRangeReadings(patientId, fromInstant, toInstant);
    }

    @GetMapping("/summary/{patientId}")
    public HealthSummaryDTO getSummary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String patientId) {
        authService.requirePatientOrDoctorForPatient(authorization, patientId);
        return healthService.getSummary(patientId);
    }
}
