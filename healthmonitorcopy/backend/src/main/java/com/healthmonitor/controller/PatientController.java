package com.healthmonitor.controller;

import com.healthmonitor.model.Patient;
import com.healthmonitor.service.AuthService;
import com.healthmonitor.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;
    private final AuthService authService;

    @GetMapping
    public List<Patient> getAll(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireDoctor(authorization);
        return patientService.getAllPatients();
    }

    @GetMapping("/{id}")
    public Patient getById(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        authService.requirePatientOrDoctorForPatient(authorization, id);
        return patientService.getById(id);
    }

    @GetMapping("/doctor/{doctorId}")
    public List<Patient> getByDoctor(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String doctorId) {
        authService.requireDoctor(authorization);
        return patientService.getPatientsByDoctor(doctorId);
    }

    @GetMapping("/status/{status}")
    public List<Patient> getByStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String status) {
        authService.requireDoctor(authorization);
        return patientService.getPatientsByStatus(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Patient create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody Patient patient) {
        authService.requireDoctor(authorization);
        return patientService.createPatient(patient);
    }

    @PutMapping("/{id}")
    public Patient update(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @Valid @RequestBody Patient patient) {
        authService.requireDoctor(authorization);
        return patientService.updatePatient(id, patient);
    }

    @PutMapping("/{id}/device")
    public Patient linkDevice(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        authService.requireDoctor(authorization);
        String deviceId = body.get("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            throw new RuntimeException("deviceId is required");
        }
        return patientService.linkDevice(id, deviceId);
    }

    @DeleteMapping("/{id}")
    public void delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        authService.requireDoctor(authorization);
        patientService.deletePatient(id);
    }
}
