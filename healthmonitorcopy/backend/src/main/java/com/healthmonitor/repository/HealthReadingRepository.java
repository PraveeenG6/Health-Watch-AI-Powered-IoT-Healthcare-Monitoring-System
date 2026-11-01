package com.healthmonitor.repository;

import com.healthmonitor.model.HealthReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HealthReadingRepository extends MongoRepository<HealthReading, String> {

    Optional<HealthReading> findTopByPatientIdOrderByRecordedAtDesc(String patientId);

    long countByPatientId(String patientId);

    Page<HealthReading> findByPatientIdOrderByRecordedAtDesc(String patientId, Pageable pageable);

    List<HealthReading> findByPatientIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            String patientId, Instant from, Instant to);

    List<HealthReading> findByPatientIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(
            String patientId, Instant start);
}
