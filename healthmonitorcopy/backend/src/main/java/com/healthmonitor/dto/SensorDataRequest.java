package com.healthmonitor.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SensorDataRequest {

    private String patientId;

    @NotBlank(message = "deviceId is required")
    private String deviceId;

    @JsonProperty("xAccel")
    @JsonAlias({"xacc", "x_accel", "x"})
    @DecimalMin(value = "-100.0", message = "xAccel out of range")
    @DecimalMax(value = "100.0", message = "xAccel out of range")
    private double xAccel;

    @JsonProperty("yAccel")
    @JsonAlias({"yacc", "y_accel", "y"})
    @DecimalMin(value = "-100.0", message = "yAccel out of range")
    @DecimalMax(value = "100.0", message = "yAccel out of range")
    private double yAccel;

    @JsonProperty("zAccel")
    @JsonAlias({"zacc", "z_accel", "z"})
    @DecimalMin(value = "-100.0", message = "zAccel out of range")
    @DecimalMax(value = "100.0", message = "zAccel out of range")
    private double zAccel;

    @JsonProperty("stepCount")
    @JsonAlias({"steps", "step_count", "stepcount"})
    @DecimalMin(value = "0.0")
    private long stepCount;

    @JsonAlias("temp")
    @DecimalMin(value = "30.0", message = "Temperature too low to be valid")
    @DecimalMax(value = "45.0", message = "Temperature too high to be valid")
    private double temperature;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "300.0", message = "Heart rate out of range")
    private double heartRate;

    @DecimalMin(value = "0.0")
    @DecimalMax(value = "100.0")
    private double spo2;

    /**
     * Sensor timestamp from ESP32. Accepts ISO-8601 text or epoch seconds/millis
     * as a string; HealthService normalises it to an Instant for storage.
     */
    private String timestamp;
}
