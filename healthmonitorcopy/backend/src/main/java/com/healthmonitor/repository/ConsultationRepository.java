package com.healthmonitor.repository;

import com.healthmonitor.model.Consultation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConsultationRepository extends MongoRepository<Consultation, String> {

    List<Consultation> findByPatientIdOrderByConsultationTimeDesc(String patientId);
}
