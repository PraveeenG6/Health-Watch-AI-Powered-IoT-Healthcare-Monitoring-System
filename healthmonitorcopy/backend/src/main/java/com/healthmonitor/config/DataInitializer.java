package com.healthmonitor.config;

import com.healthmonitor.model.AppUser;
import com.healthmonitor.model.Consultation;
import com.healthmonitor.model.HealthReading;
import com.healthmonitor.model.Patient;
import com.healthmonitor.repository.AppUserRepository;
import com.healthmonitor.repository.ConsultationRepository;
import com.healthmonitor.repository.HealthReadingRepository;
import com.healthmonitor.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String DEMO_DEVICE_ID = "ESP32-DEMO01";
    private static final String PATIENT_USER_ID = "patient1";
    private static final String LEGACY_PATIENT_USER_ID = "patient_demo";
    private static final String PATIENT_PASSWORD = "miniproject";
    private static final String PATIENT_NAME = "John Doe";
    private static final String DOCTOR_USER_ID = "doctor1";
    private static final String LEGACY_DOCTOR_USER_ID = "doctor_demo";
    private static final String DOCTOR_PASSWORD = "miniproject";
    private static final String DOCTOR_NAME = "Dr. Robert Smith";

    private final PatientRepository patientRepository;
    private final AppUserRepository appUserRepository;
    private final HealthReadingRepository healthReadingRepository;
    private final ConsultationRepository consultationRepository;

    @Override
    public void run(String... args) {
        try {
            Patient demoPatient = patientRepository.findByDeviceId(DEMO_DEVICE_ID)
                    .orElseGet(this::createDemoPatient);
            demoPatient = updateDemoPatient(demoPatient);

            seedUsers(demoPatient);
            seedReadings(demoPatient.getId());
            seedConsultation(demoPatient.getId());

            log.info("Demo login users: {}/{} and {}/{}",
                    PATIENT_USER_ID, PATIENT_PASSWORD, DOCTOR_USER_ID, DOCTOR_PASSWORD);
        } catch (Exception e) {
            log.warn("DataInitializer: Could not initialize demo data - MongoDB may be unavailable. Error: {}", e.getMessage());
        }
    }

    private Patient createDemoPatient() {
        Patient patient = Patient.builder()
                .name(PATIENT_NAME)
                .email("patient@healthmonitor.com")
                .phone("+91 9876543210")
                .dateOfBirth(LocalDate.of(2001, 5, 15))
                .gender("Male")
                .bloodType("O+")
                .medicalHistory("No known allergies")
                .assignedDoctorId(DOCTOR_USER_ID)
                .deviceId(DEMO_DEVICE_ID)
                .status("ACTIVE")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return patientRepository.save(patient);
    }

    private Patient updateDemoPatient(Patient patient) {
        patient.setName(PATIENT_NAME);
        patient.setAssignedDoctorId(DOCTOR_USER_ID);
        patient.setDeviceId(DEMO_DEVICE_ID);
        patient.setStatus("ACTIVE");
        patient.setUpdatedAt(Instant.now());
        return patientRepository.save(patient);
    }

    private void seedUsers(Patient patient) {
        upsertUser(PATIENT_USER_ID, LEGACY_PATIENT_USER_ID, PATIENT_PASSWORD,
                patient.getName(), "PATIENT", patient.getId());
        upsertUser(DOCTOR_USER_ID, LEGACY_DOCTOR_USER_ID, DOCTOR_PASSWORD,
                DOCTOR_NAME, "DOCTOR", null);
    }

    private void upsertUser(
            String userId,
            String legacyUserId,
            String password,
            String name,
            String role,
            String patientId) {
        AppUser user = appUserRepository.findByUserId(userId)
                .or(() -> appUserRepository.findByUserId(legacyUserId))
                .orElseGet(() -> AppUser.builder()
                        .createdAt(Instant.now())
                        .build());

        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }

        user.setUserId(userId);
        user.setPassword(password);
        user.setName(name);
        user.setRole(role);
        user.setPatientId(patientId);
        appUserRepository.save(user);
        deleteLegacyUserIfPresent(userId, legacyUserId);
    }

    private void deleteLegacyUserIfPresent(String userId, String legacyUserId) {
        appUserRepository.findByUserId(legacyUserId)
                .filter(legacyUser -> !userId.equals(legacyUser.getUserId()))
                .ifPresent(appUserRepository::delete);
    }

    private void seedReadings(String patientId) {
        if (healthReadingRepository.countByPatientId(patientId) > 0) {
            return;
        }

        Instant now = Instant.now();
        List<HealthReading> readings = List.of(
                demoReading(patientId, 0.02, 0.03, 1.01, 82, 98, 36.7, 2600,
                        now.minus(Duration.ofHours(6)), "NORMAL"),
                demoReading(patientId, -0.04, 0.05, 0.98, 88, 97, 36.8, 3200,
                        now.minus(Duration.ofHours(5)), "NORMAL"),
                demoReading(patientId, 0.06, -0.02, 1.02, 96, 96, 37.1, 4100,
                        now.minus(Duration.ofHours(4)), "NORMAL"),
                demoReading(patientId, 0.10, 0.08, 1.04, 112, 95, 37.6, 4800,
                        now.minus(Duration.ofHours(3)), "CAUTION"),
                demoReading(patientId, -0.03, 0.04, 1.00, 104, 96, 37.2, 5400,
                        now.minus(Duration.ofHours(2)), "NORMAL"),
                demoReading(patientId, 0.01, -0.01, 1.01, 78, 98, 36.6, 6200,
                        now.minus(Duration.ofHours(1)), "NORMAL")
        );
        healthReadingRepository.saveAll(readings);
    }

    private HealthReading demoReading(
            String patientId, double xAccel, double yAccel, double zAccel,
            double heartRate, double spo2, double temperature,
            long steps, Instant recordedAt, String aiRiskLevel) {
        return HealthReading.builder()
                .patientId(patientId)
                .deviceId(DEMO_DEVICE_ID)
                .xAccel(xAccel)
                .yAccel(yAccel)
                .zAccel(zAccel)
                .heartRate(heartRate)
                .spo2(spo2)
                .temperature(temperature)
                .stepCount(steps)
                .fallDetected(false)
                .fallProbability(0.04)
                .fallAlert(false)
                .timestamp(recordedAt)
                .heartRateStatus(heartRate > 110 ? "HIGH" : "NORMAL")
                .spo2Status(spo2 < 94 ? "LOW" : "NORMAL")
                .tempStatus(temperature > 38.5 ? "HIGH" : "NORMAL")
                .aiRiskLevel(aiRiskLevel)
                .aiRiskConfidence("CAUTION".equals(aiRiskLevel) ? 0.72 : 0.91)
                .aiMessage("CAUTION".equals(aiRiskLevel)
                        ? "Vitals need monitoring. Recheck the patient and continue observation."
                        : "No fall pattern detected and vitals are within configured limits.")
                .recordedAt(recordedAt)
                .build();
    }

    private void seedConsultation(String patientId) {
        List<Consultation> consultations = consultationRepository.findByPatientIdOrderByConsultationTimeDesc(patientId);
        if (!consultations.isEmpty()) {
            consultations.forEach(consultation -> {
                if (LEGACY_DOCTOR_USER_ID.equals(consultation.getDoctorId())
                        || DOCTOR_USER_ID.equals(consultation.getDoctorId())) {
                    consultation.setDoctorId(DOCTOR_USER_ID);
                    consultation.setDoctorName(DOCTOR_NAME);
                }
            });
            consultationRepository.saveAll(consultations);
            return;
        }

        consultationRepository.save(Consultation.builder()
                .patientId(patientId)
                .doctorId(DOCTOR_USER_ID)
                .doctorName(DOCTOR_NAME)
                .consultationTime(Instant.now().minus(Duration.ofDays(1)))
                .suggestions("Continue regular monitoring, drink enough water, and report if breathlessness or fever appears.")
                .createdAt(Instant.now())
                .build());
    }
}
