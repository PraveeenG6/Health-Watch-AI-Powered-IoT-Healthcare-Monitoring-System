package com.healthmonitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SosRequest {

    @NotBlank(message = "patientId is required")
    private String patientId;

    private String message;
    private String triggeredBy;
    private String emergencyContact;
}
