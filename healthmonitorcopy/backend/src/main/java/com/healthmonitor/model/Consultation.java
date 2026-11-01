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
@Document(collection = "consultations")
public class Consultation {

    @Id
    private String id;

    @Indexed
    private String patientId;

    private String doctorId;
    private String doctorName;
    private Instant consultationTime;
    private String suggestions;
    private Instant createdAt;
}
