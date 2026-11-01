package com.healthmonitor.repository;

import com.healthmonitor.model.Patient;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends MongoRepository<Patient, String> {

    Optional<Patient> findByDeviceId(String deviceId);

    List<Patient> findByAssignedDoctorId(String doctorId);

    List<Patient> findByStatus(String status);
}
