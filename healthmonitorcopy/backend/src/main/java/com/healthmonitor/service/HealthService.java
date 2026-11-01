package com.healthmonitor.service;

import com.healthmonitor.dto.AlertResponse;
import com.healthmonitor.dto.HealthSummaryDTO;
import com.healthmonitor.dto.SensorDataRequest;
import com.healthmonitor.model.Alert;
import com.healthmonitor.model.HealthReading;
import com.healthmonitor.model.Patient;
import com.healthmonitor.repository.AlertRepository;
import com.healthmonitor.repository.HealthReadingRepository;
import com.healthmonitor.repository.PatientRepository;
import com.healthmonitor.service.ai.HealthAiService;
import com.healthmonitor.service.ai.HealthAiService.HealthAiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.OptionalDouble;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthReadingRepository healthReadingRepository;
    private final AlertRepository alertRepository;
    private final PatientRepository patientRepository;
    private final AlertService alertService;
    private final HealthAiService healthAiService;

    @Value("${app.alert.heart-rate.high:110}")
    private double hrHigh;

    @Value("${app.alert.heart-rate.low:45}")
    private double hrLow;

    @Value("${app.alert.spo2.low:94}")
    private double spo2Low;

    @Value("${app.alert.temperature.high:38.5}")
    private double tempHigh;

    @Value("${app.alert.temperature.low:35.0}")
    private double tempLow;

    public AlertResponse processSensorData(SensorDataRequest request) {
        String patientId = resolvePatientId(request);
        Instant sensorTimestamp = parseSensorTimestamp(request.getTimestamp());
        String heartRateStatus = evaluateHeartRate(request.getHeartRate());
        String spo2Status = evaluateSpo2(request.getSpo2());
        String tempStatus = evaluateTemperature(request.getTemperature());
        HealthAiResult aiResult = healthAiService.analyze(
                request.getXAccel(),
                request.getYAccel(),
                request.getZAccel(),
                request.getHeartRate(),
                request.getSpo2(),
                request.getTemperature());
        boolean fallDetected = aiResult.fallDetected();

        HealthReading reading = HealthReading.builder()
                .patientId(patientId)
                .deviceId(request.getDeviceId())
                .xAccel(request.getXAccel())
                .yAccel(request.getYAccel())
                .zAccel(request.getZAccel())
                .temperature(request.getTemperature())
                .heartRate(request.getHeartRate())
                .spo2(request.getSpo2())
                .stepCount(request.getStepCount())
                .fallDetected(fallDetected)
                .fallProbability(aiResult.fallProbability())
                .fallAlert(fallDetected)
                .timestamp(sensorTimestamp)
                .heartRateStatus(heartRateStatus)
                .spo2Status(spo2Status)
                .tempStatus(tempStatus)
                .aiRiskLevel(aiResult.riskLevel())
                .aiRiskConfidence(aiResult.confidence())
                .aiMessage(aiResult.message())
                .recordedAt(Instant.now())
                .build();

        reading = healthReadingRepository.save(reading);
        List<Alert> newAlerts = alertService.createAlertsForReading(reading);
        Alert deviceAlert = chooseDeviceAlert(newAlerts);

        boolean heartRateAlert = !"NORMAL".equals(heartRateStatus);
        boolean spo2Alert = !"NORMAL".equals(spo2Status);
        boolean tempAlert = !"NORMAL".equals(tempStatus);
        boolean aiAlert = !"NORMAL".equals(aiResult.riskLevel());
        boolean anyAlert = heartRateAlert || spo2Alert || tempAlert || fallDetected || aiAlert;

        return AlertResponse.builder()
                .heartRateAlert(heartRateAlert)
                .spo2Alert(spo2Alert)
                .tempAlert(tempAlert)
                .fallAlert(fallDetected)
                .aiAlert(aiAlert)
                .anyAlert(anyAlert)
                .sendAlertToDevice(deviceAlert != null)
                .message(buildMessage(fallDetected, heartRateAlert, spo2Alert, tempAlert, aiAlert, deviceAlert != null))
                .deviceAlertType(deviceAlert != null ? deviceAlert.getAlertType() : null)
                .deviceAlertMessage(deviceAlert != null ? deviceAlert.getMessage() : null)
                .heartRateStatus(heartRateStatus)
                .spo2Status(spo2Status)
                .tempStatus(tempStatus)
                .severity(findSeverity(fallDetected, heartRateAlert, spo2Alert, tempAlert, aiResult.riskLevel()))
                .aiRiskLevel(aiResult.riskLevel())
                .aiRiskConfidence(aiResult.confidence())
                .modelFallDetected(fallDetected)
                .fallProbability(aiResult.fallProbability())
                .patientId(patientId)
                .readingId(reading.getId())
                .build();
    }

    public HealthReading getLatestReading(String patientId) {
        return healthReadingRepository.findTopByPatientIdOrderByRecordedAtDesc(patientId).orElse(null);
    }

    public Page<HealthReading> getHistory(String patientId, Pageable pageable) {
        return healthReadingRepository.findByPatientIdOrderByRecordedAtDesc(patientId, pageable);
    }

    public List<HealthReading> getRangeReadings(String patientId, Instant from, Instant to) {
        return healthReadingRepository.findByPatientIdAndRecordedAtBetweenOrderByRecordedAtAsc(patientId, from, to);
    }

    public HealthSummaryDTO getSummary(String patientId) {
        Instant startOfDay = ZonedDateTime.now(ZoneId.of("UTC"))
                .toLocalDate()
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant();

        HealthReading latest = getLatestReading(patientId);
        List<HealthReading> todayReadings =
                healthReadingRepository.findByPatientIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(patientId, startOfDay);

        OptionalDouble avgHeartRate = todayReadings.stream().mapToDouble(HealthReading::getHeartRate).average();
        OptionalDouble avgTemperature = todayReadings.stream().mapToDouble(HealthReading::getTemperature).average();
        OptionalDouble avgSpo2 = todayReadings.stream().mapToDouble(HealthReading::getSpo2).average();

        long activeAlerts = alertRepository.countByPatientIdAndActiveTrue(patientId);
        long fallsToday = alertRepository.countByPatientIdAndAlertTypeAndTriggeredAtBetween(
                patientId, "FALL", startOfDay, Instant.now());
        long heartRateAlertsToday =
                alertRepository.countByPatientIdAndAlertTypeAndTriggeredAtBetween(
                        patientId, "HEART_RATE_HIGH", startOfDay, Instant.now())
                        + alertRepository.countByPatientIdAndAlertTypeAndTriggeredAtBetween(
                        patientId, "HEART_RATE_LOW", startOfDay, Instant.now());

        String patientName = patientRepository.findById(patientId)
                .map(Patient::getName)
                .orElse("Unknown");

        return HealthSummaryDTO.builder()
                .patientId(patientId)
                .patientName(patientName)
                .latestTemperature(latest != null ? latest.getTemperature() : 0)
                .latestHeartRate(latest != null ? latest.getHeartRate() : 0)
                .latestSpo2(latest != null ? latest.getSpo2() : 0)
                .latestStepCount(latest != null ? latest.getStepCount() : 0)
                .latestFallDetected(latest != null && latest.isFallDetected())
                .latestFallProbability(latest != null ? latest.getFallProbability() : 0)
                .heartRateStatus(latest != null ? latest.getHeartRateStatus() : "UNKNOWN")
                .spo2Status(latest != null ? latest.getSpo2Status() : "UNKNOWN")
                .tempStatus(latest != null ? latest.getTempStatus() : "UNKNOWN")
                .aiRiskLevel(latest != null ? latest.getAiRiskLevel() : "UNKNOWN")
                .aiRiskConfidence(latest != null ? latest.getAiRiskConfidence() : 0)
                .avgHeartRate24h(avgHeartRate.orElse(0))
                .avgTemperature24h(avgTemperature.orElse(0))
                .avgSpo2_24h(avgSpo2.orElse(0))
                .totalSteps24h(latest != null ? latest.getStepCount() : 0)
                .activeAlerts(activeAlerts)
                .fallsToday(fallsToday)
                .heartRateAlertsToday(heartRateAlertsToday)
                .lastUpdated(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .build();
    }

    private String resolvePatientId(SensorDataRequest request) {
        if (request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            return patientRepository.findByDeviceId(request.getDeviceId())
                    .map(Patient::getId)
                    .orElseGet(() -> resolveRequestPatientId(request));
        }

        return resolveRequestPatientId(request);
    }

    private String resolveRequestPatientId(SensorDataRequest request) {
        if (request.getPatientId() != null && !request.getPatientId().isBlank()) {
            return request.getPatientId();
        }

        return "UNREGISTERED-" + request.getDeviceId();
    }

    private Instant parseSensorTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }

        String trimmed = timestamp.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            try {
                long epochValue = Long.parseLong(trimmed);
                return Math.abs(epochValue) < 10_000_000_000L
                        ? Instant.ofEpochSecond(epochValue)
                        : Instant.ofEpochMilli(epochValue);
            } catch (NumberFormatException e) {
                return Instant.now();
            }
        }
    }

    private String evaluateHeartRate(double heartRate) {
        if (heartRate > hrHigh) {
            return "HIGH";
        }
        if (heartRate > 0 && heartRate < hrLow) {
            return "LOW";
        }
        return "NORMAL";
    }

    private String evaluateSpo2(double spo2) {
        return spo2 < spo2Low ? "LOW" : "NORMAL";
    }

    private String evaluateTemperature(double temperature) {
        if (temperature > tempHigh) {
            return "HIGH";
        }
        if (temperature < tempLow) {
            return "LOW";
        }
        return "NORMAL";
    }

    private String findSeverity(boolean fall, boolean heartRate, boolean spo2, boolean temperature, String aiRiskLevel) {
        if (fall || spo2 || "EMERGENCY".equals(aiRiskLevel)) {
            return "CRITICAL";
        }
        if (heartRate || temperature || "CAUTION".equals(aiRiskLevel)) {
            return "WARNING";
        }
        return "OK";
    }

    private Alert chooseDeviceAlert(List<Alert> newAlerts) {
        return newAlerts.stream()
                .filter(alert -> "FALL".equals(alert.getAlertType()))
                .findFirst()
                .or(() -> newAlerts.stream()
                        .filter(alert -> "SPO2_LOW".equals(alert.getAlertType()))
                        .findFirst())
                .or(() -> newAlerts.stream()
                        .filter(alert -> alert.getAlertType() != null && alert.getAlertType().startsWith("HEART_RATE"))
                        .findFirst())
                .or(() -> newAlerts.stream()
                        .filter(alert -> alert.getAlertType() != null && alert.getAlertType().startsWith("TEMP"))
                        .findFirst())
                .orElse(null);
    }

    private String buildMessage(
            boolean fall,
            boolean heartRate,
            boolean spo2,
            boolean temperature,
            boolean aiAlert,
            boolean newDeviceAlert) {
        if (!newDeviceAlert && (fall || heartRate || spo2 || temperature || aiAlert)) {
            return "Alert condition active";
        }
        if (fall) {
            return "Fall detected";
        }
        if (spo2) {
            return "Low oxygen alert";
        }
        if (heartRate) {
            return "Heart rate alert";
        }
        if (temperature) {
            return "Temperature alert";
        }
        if (aiAlert) {
            return "AI risk detected";
        }
        return "All OK";
    }
}
