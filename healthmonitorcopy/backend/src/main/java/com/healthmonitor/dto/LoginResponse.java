package com.healthmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String userId;
    private String name;
    private String role;
    private String patientId;
}
