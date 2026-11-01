package com.healthmonitor.service;

import com.healthmonitor.dto.ConsultationRequest;
import com.healthmonitor.model.AppUser;
import com.healthmonitor.model.Consultation;
import com.healthmonitor.repository.ConsultationRepository;
import com.healthmonitor.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final PatientRepository patientRepository;

    public Consultation saveConsultation(ConsultationRequest request, AppUser doctor) {
        patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found: " + request.getPatientId()));

        Consultation consultation = Consultation.builder()
                .patientId(request.getPatientId())
                .doctorId(doctor.getUserId())
                .doctorName(doctor.getName())
                .consultationTime(request.getConsultationTime() == null ? Instant.now() : request.getConsultationTime())
                .suggestions(request.getSuggestions())
                .createdAt(Instant.now())
                .build();

        return consultationRepository.save(consultation);
    }

    public List<Consultation> getForPatient(String patientId) {
        return consultationRepository.findByPatientIdOrderByConsultationTimeDesc(patientId);
    }
}
