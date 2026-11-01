package com.healthmonitor.repository;

import com.healthmonitor.model.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface AlertRepository extends MongoRepository<Alert, String> {

    List<Alert> findByActiveTrueOrderByTriggeredAtDesc();

    List<Alert> findByPatientIdAndActiveTrueOrderByTriggeredAtDesc(String patientId);

    Page<Alert> findByPatientIdOrderByTriggeredAtDesc(String patientId, Pageable pageable);

    long countByPatientIdAndActiveTrue(String patientId);

    boolean existsByPatientIdAndAlertTypeAndActiveTrue(String patientId, String alertType);

    long countByPatientIdAndAlertTypeAndTriggeredAtBetween(
            String patientId, String alertType, Instant from, Instant to);
}
