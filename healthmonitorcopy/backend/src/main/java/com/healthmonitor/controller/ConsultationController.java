package com.healthmonitor.controller;

import com.healthmonitor.dto.ConsultationRequest;
import com.healthmonitor.model.AppUser;
import com.healthmonitor.model.Consultation;
import com.healthmonitor.service.AuthService;
import com.healthmonitor.service.ConsultationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;
    private final AuthService authService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Consultation create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ConsultationRequest request) {
        AppUser doctor = authService.requireDoctor(authorization);
        return consultationService.saveConsultation(request, doctor);
    }

    @GetMapping("/patient/{patientId}")
    public List<Consultation> getForPatient(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String patientId) {
        authService.requirePatientOrDoctorForPatient(authorization, patientId);
        return consultationService.getForPatient(patientId);
    }
}
