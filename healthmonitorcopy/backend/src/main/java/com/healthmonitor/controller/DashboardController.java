package com.healthmonitor.controller;

import com.healthmonitor.dto.HealthSummaryDTO;
import com.healthmonitor.model.Alert;
import com.healthmonitor.model.HealthReading;
import com.healthmonitor.model.Patient;
import com.healthmonitor.service.AlertService;
import com.healthmonitor.service.AuthService;
import com.healthmonitor.service.ConsultationService;
import com.healthmonitor.service.HealthService;
import com.healthmonitor.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final HealthService healthService;
    private final AlertService alertService;
    private final PatientService patientService;
    private final ConsultationService consultationService;
    private final AuthService authService;

    @GetMapping("/patient/{patientId}")
    public Map<String, Object> patientDashboard(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String patientId) {
        authService.requirePatientOrDoctorForPatient(authorization, patientId);
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("summary", healthService.getSummary(patientId));
        dashboard.put("activeAlerts", alertService.getActiveAlertsForPatient(patientId));
        dashboard.put("recentReadings", healthService.getHistory(
                patientId, PageRequest.of(0, 20, Sort.by("recordedAt").descending())).getContent());
        dashboard.put("consultations", consultationService.getForPatient(patientId));
        return dashboard;
    }

    @GetMapping("/doctor/{doctorId}/overview")
    public List<Map<String, Object>> doctorOverview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String doctorId) {
        authService.requireDoctor(authorization);
        return patientService.getAllPatients().stream()
                .map(this::patientOverview)
                .toList();
    }

    @GetMapping("/doctor/{doctorId}/patient/{patientId}")
    public Map<String, Object> doctorPatientView(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String doctorId,
            @PathVariable String patientId) {
        authService.requireDoctor(authorization);
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("summary", healthService.getSummary(patientId));
        dashboard.put("activeAlerts", alertService.getActiveAlertsForPatient(patientId));
        dashboard.put("readings", healthService.getHistory(
                patientId, PageRequest.of(0, 50, Sort.by("recordedAt").descending())).getContent());
        dashboard.put("alertHistory", alertService.getAlertHistory(
                patientId, PageRequest.of(0, 20, Sort.by("triggeredAt").descending())).getContent());
        dashboard.put("consultations", consultationService.getForPatient(patientId));
        return dashboard;
    }

    @GetMapping("/alerts/active")
    public List<Alert> allActiveAlerts(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireDoctor(authorization);
        return alertService.getActiveAlerts();
    }

    private Map<String, Object> patientOverview(Patient patient) {
        HealthReading latest = healthService.getLatestReading(patient.getId());
        HealthSummaryDTO summary = healthService.getSummary(patient.getId());

        Map<String, Object> row = new HashMap<>();
        row.put("patient", patient);
        row.put("latestReading", latest);
        row.put("summary", summary);
        row.put("activeAlerts", alertService.getActiveAlertsForPatient(patient.getId()).size());
        return row;
    }
}
