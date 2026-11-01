package com.healthmonitor.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthSummaryDTO {

    private String patientId;
    private String patientName;

    private double latestTemperature;
    private double latestHeartRate;
    private double latestSpo2;
    private long latestStepCount;
    private boolean latestFallDetected;
    private double latestFallProbability;

    private String heartRateStatus;
    private String spo2Status;
    private String tempStatus;

    private String aiRiskLevel;
    private double aiRiskConfidence;

    private double avgTemperature24h;
    private double avgHeartRate24h;
    private double avgSpo2_24h;
    private long totalSteps24h;

    private long activeAlerts;
    private long fallsToday;
    private long heartRateAlertsToday;

    private String lastUpdated;
}
