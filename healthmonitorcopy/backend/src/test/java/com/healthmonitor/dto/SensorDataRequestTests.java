package com.healthmonitor.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensorDataRequestTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsCamelCaseSensorFields() throws Exception {
        String json = """
                {
                  "patientId": "patient-1",
                  "deviceId": "ESP32-DEMO01",
                  "xAccel": 1.0,
                  "yAccel": 1.1,
                  "zAccel": 1.02,
                  "heartRate": 99,
                  "spo2": 98,
                  "temperature": 35.3,
                  "stepCount": 1234,
                  "timestamp": "2026-05-08T10:30:00Z"
                }
                """;

        SensorDataRequest request = objectMapper.readValue(json, SensorDataRequest.class);

        assertThat(request.getXAccel()).isEqualTo(1.0);
        assertThat(request.getYAccel()).isEqualTo(1.1);
        assertThat(request.getZAccel()).isEqualTo(1.02);
        assertThat(request.getStepCount()).isEqualTo(1234);
    }

    @Test
    void mapsEsp32AliasSensorFields() throws Exception {
        String json = """
                {
                  "deviceId": "ESP32-DEMO01",
                  "xacc": 0.05,
                  "yacc": -0.02,
                  "zacc": 1.02,
                  "steps": 4321,
                  "heartRate": 99,
                  "spo2": 98,
                  "temp": 35.3
                }
                """;

        SensorDataRequest request = objectMapper.readValue(json, SensorDataRequest.class);

        assertThat(request.getXAccel()).isEqualTo(0.05);
        assertThat(request.getYAccel()).isEqualTo(-0.02);
        assertThat(request.getZAccel()).isEqualTo(1.02);
        assertThat(request.getStepCount()).isEqualTo(4321);
        assertThat(request.getTemperature()).isEqualTo(35.3);
    }
}
