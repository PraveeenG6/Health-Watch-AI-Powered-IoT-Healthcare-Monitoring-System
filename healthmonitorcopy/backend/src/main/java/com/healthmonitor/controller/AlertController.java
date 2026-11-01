package com.healthmonitor.controller;

import com.healthmonitor.model.Alert;
import com.healthmonitor.service.AlertService;
import com.healthmonitor.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final AuthService authService;

    @GetMapping("/active")
    public List<Alert> getAllActive(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireDoctor(authorization);
        return alertService.getActiveAlerts();
    }

    @GetMapping("/active/{patientId}")
    public List<Alert> getActiveForPatient(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String patientId) {
        authService.requirePatientOrDoctorForPatient(authorization, patientId);
        return alertService.getActiveAlertsForPatient(patientId);
    }

    @GetMapping("/history/{patientId}")
    public Page<Alert> getHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        authService.requirePatientOrDoctorForPatient(authorization, patientId);
        return alertService.getAlertHistory(
                patientId, PageRequest.of(page, size, Sort.by("triggeredAt").descending()));
    }

    @PutMapping("/{alertId}/acknowledge")
    public Alert acknowledge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String alertId,
            @RequestBody(required = false) Map<String, String> body) {
        authService.requireDoctor(authorization);
        String note = body == null ? "" : body.getOrDefault("note", "");
        return alertService.acknowledgeAlert(alertId, note);
    }
}
