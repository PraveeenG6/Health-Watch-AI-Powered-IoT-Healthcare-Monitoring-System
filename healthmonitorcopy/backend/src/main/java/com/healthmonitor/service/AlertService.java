package com.healthmonitor.service;

import com.healthmonitor.model.Alert;
import com.healthmonitor.model.HealthReading;
import com.healthmonitor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    public List<Alert> createAlertsForReading(HealthReading reading) {
        List<Alert> alerts = new ArrayList<>();
        Instant now = Instant.now();
        
        // If all readings are normal, auto-deactivate previous alerts for this patient
        boolean isAllNormal = "NORMAL".equals(reading.getHeartRateStatus())
                && "NORMAL".equals(reading.getSpo2Status())
                && "NORMAL".equals(reading.getTempStatus())
                && !reading.isFallAlert()
                && "NORMAL".equals(reading.getAiRiskLevel());
        
        if (isAllNormal) {
            deactivateActiveAlertsForPatient(reading.getPatientId());
            return alerts;
        }

        if (reading.isFallAlert()) {
            addAlertIfNew(alerts, reading, "FALL",
                    "Fall detected by DL4J model. Probability: " + reading.getFallProbability(),
                    "CRITICAL", now);
        }

        if ("HIGH".equals(reading.getHeartRateStatus())) {
            addAlertIfNew(alerts, reading, "HEART_RATE_HIGH",
                    "High heart rate: " + Math.round(reading.getHeartRate()) + " BPM", "WARNING", now);
        }

        if ("LOW".equals(reading.getHeartRateStatus())) {
            addAlertIfNew(alerts, reading, "HEART_RATE_LOW",
                    "Low heart rate: " + Math.round(reading.getHeartRate()) + " BPM", "WARNING", now);
        }

        if ("LOW".equals(reading.getSpo2Status())) {
            addAlertIfNew(alerts, reading, "SPO2_LOW",
                    "Low oxygen level: " + reading.getSpo2() + "%", "CRITICAL", now);
        }

        if ("HIGH".equals(reading.getTempStatus())) {
            addAlertIfNew(alerts, reading, "TEMP_HIGH",
                    "High temperature: " + reading.getTemperature() + " C", "WARNING", now);
        }

        if ("LOW".equals(reading.getTempStatus())) {
            addAlertIfNew(alerts, reading, "TEMP_LOW",
                    "Low temperature: " + reading.getTemperature() + " C", "WARNING", now);
        }

        return alerts.isEmpty() ? alerts : alertRepository.saveAll(alerts);
    }
    
    private void deactivateActiveAlertsForPatient(String patientId) {
        List<Alert> activeAlerts = alertRepository.findByPatientIdAndActiveTrueOrderByTriggeredAtDesc(patientId);
        for (Alert alert : activeAlerts) {
            if (!"SOS".equals(alert.getAlertType())) {
                alert.setActive(false);
                alert.setAcknowledgedAt(Instant.now());
            }
        }
        if (!activeAlerts.isEmpty()) {
            alertRepository.saveAll(activeAlerts);
        }
    }

    public List<Alert> getActiveAlerts() {
        return alertRepository.findByActiveTrueOrderByTriggeredAtDesc();
    }

    public List<Alert> getActiveAlertsForPatient(String patientId) {
        return alertRepository.findByPatientIdAndActiveTrueOrderByTriggeredAtDesc(patientId);
    }

    public Page<Alert> getAlertHistory(String patientId, Pageable pageable) {
        return alertRepository.findByPatientIdOrderByTriggeredAtDesc(patientId, pageable);
    }

    public Alert acknowledgeAlert(String alertId, String note) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + alertId));
        alert.setActive(false);
        alert.setDoctorNote(note);
        alert.setAcknowledgedAt(Instant.now());
        return alertRepository.save(alert);
    }

    private Alert buildAlert(HealthReading reading, String type, String message, String severity, Instant now) {
        return Alert.builder()
                .patientId(reading.getPatientId())
                .readingId(reading.getId())
                .alertType(type)
                .message(message)
                .severity(severity)
                .active(true)
                .triggeredAt(now)
                .build();
    }

    private void addAlertIfNew(
            List<Alert> alerts,
            HealthReading reading,
            String type,
            String message,
            String severity,
            Instant now) {
        boolean alreadyActive = alertRepository.existsByPatientIdAndAlertTypeAndActiveTrue(
                reading.getPatientId(), type);
        if (!alreadyActive) {
            alerts.add(buildAlert(reading, type, message, severity, now));
        }
    }
}
