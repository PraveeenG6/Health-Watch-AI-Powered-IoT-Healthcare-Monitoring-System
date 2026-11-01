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
@Document(collection = "alerts")
public class Alert {

    @Id
    private String id;

    @Indexed
    private String patientId;

    private String readingId;
    private String alertType;
    private String message;
    private String severity;

    @Builder.Default
    private boolean active = true;

    private String doctorNote;

    @Indexed
    private Instant triggeredAt;

    private Instant acknowledgedAt;
}
