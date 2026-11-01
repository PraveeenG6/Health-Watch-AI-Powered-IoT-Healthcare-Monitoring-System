package com.healthmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertResponse {

    private boolean heartRateAlert;
    private boolean spo2Alert;
    private boolean fallAlert;
    private boolean tempAlert;
    private boolean aiAlert;
    private boolean anyAlert;
    private boolean sendAlertToDevice;

    private String message;
    private String deviceAlertType;
    private String deviceAlertMessage;
    private String heartRateStatus;
    private String spo2Status;
    private String tempStatus;
    private String severity;
    private String aiRiskLevel;
    private double aiRiskConfidence;
    private boolean modelFallDetected;
    private double fallProbability;
    private String patientId;
    private String readingId;
}
