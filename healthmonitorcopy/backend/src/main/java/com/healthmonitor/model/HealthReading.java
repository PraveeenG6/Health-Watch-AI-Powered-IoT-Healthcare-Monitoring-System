package com.healthmonitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "health_readings")
public class HealthReading {

    @Id
    private String id;

    @Indexed
    private String patientId;

    @Indexed
    private String deviceId;

    private double xAccel;
    private double yAccel;
    private double zAccel;

    private double temperature;
    private double heartRate;
    private double spo2;
    private long stepCount;
    private boolean fallDetected;
    private double fallProbability;
    private Instant timestamp;

    private String heartRateStatus;
    private String spo2Status;
    private String tempStatus;
    private boolean fallAlert;

    private String aiRiskLevel;
    private double aiRiskConfidence;
    private String aiMessage;

    @Indexed
    private Instant recordedAt;
}
