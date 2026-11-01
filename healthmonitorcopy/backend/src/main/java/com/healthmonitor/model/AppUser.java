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
@Document(collection = "users")
public class AppUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    // Plain text is used only to keep this student demo easy to explain.
    // A real system should store a hashed password.
    private String password;

    private String name;
    private String role; // PATIENT or DOCTOR

    // Filled only for PATIENT users, so they can see only their own records.
    private String patientId;

    private Instant createdAt;
}
