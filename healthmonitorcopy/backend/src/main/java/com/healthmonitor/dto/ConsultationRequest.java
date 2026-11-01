package com.healthmonitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;

@Data
public class ConsultationRequest {

    @NotBlank
    private String patientId;

    private Instant consultationTime;

    @NotBlank
    private String suggestions;
}
