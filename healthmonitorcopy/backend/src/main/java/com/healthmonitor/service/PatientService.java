package com.healthmonitor.service;

import com.healthmonitor.model.Patient;
import com.healthmonitor.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    public List<Patient> getAllPatients() {
        return patientRepository.findAll();
    }

    public Patient getById(String id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Patient not found: " + id));
    }

    public List<Patient> getPatientsByDoctor(String doctorId) {
        return patientRepository.findByAssignedDoctorId(doctorId);
    }

    public List<Patient> getPatientsByStatus(String status) {
        return patientRepository.findByStatus(status);
    }

    public Patient createPatient(Patient patient) {
        patient.setCreatedAt(Instant.now());
        patient.setUpdatedAt(Instant.now());
        if (patient.getStatus() == null || patient.getStatus().isBlank()) {
            patient.setStatus("ACTIVE");
        }
        return patientRepository.save(patient);
    }

    public Patient updatePatient(String id, Patient updated) {
        Patient patient = getById(id);
        patient.setName(updated.getName());
        patient.setEmail(updated.getEmail());
        patient.setPhone(updated.getPhone());
        patient.setDateOfBirth(updated.getDateOfBirth());
        patient.setGender(updated.getGender());
        patient.setBloodType(updated.getBloodType());
        patient.setMedicalHistory(updated.getMedicalHistory());
        patient.setAssignedDoctorId(updated.getAssignedDoctorId());
        patient.setDeviceId(updated.getDeviceId());
        patient.setStatus(updated.getStatus());
        patient.setUpdatedAt(Instant.now());
        return patientRepository.save(patient);
    }

    public Patient linkDevice(String patientId, String deviceId) {
        Patient patient = getById(patientId);
        patient.setDeviceId(deviceId);
        patient.setUpdatedAt(Instant.now());
        return patientRepository.save(patient);
    }

    public void deletePatient(String id) {
        patientRepository.deleteById(id);
    }
}
