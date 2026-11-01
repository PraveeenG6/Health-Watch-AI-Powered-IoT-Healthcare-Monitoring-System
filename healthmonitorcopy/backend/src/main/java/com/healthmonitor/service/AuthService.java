package com.healthmonitor.service;

import com.healthmonitor.dto.LoginRequest;
import com.healthmonitor.dto.LoginResponse;
import com.healthmonitor.model.AppUser;
import com.healthmonitor.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid userId or password"));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid userId or password");
        }

        return toResponse(user);
    }

    public AppUser authenticate(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }

        if (!authorizationHeader.startsWith("Basic ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Basic authentication required");
        }

        String decoded = decodeBasicHeader(authorizationHeader.substring(6));
        int separator = decoded.indexOf(':');
        if (separator < 1) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login header");
        }

        String userId = decoded.substring(0, separator);
        String password = decoded.substring(separator + 1);

        AppUser user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid userId or password"));

        if (!user.getPassword().equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid userId or password");
        }

        return user;
    }

    public AppUser requireDoctor(String authorizationHeader) {
        AppUser user = authenticate(authorizationHeader);
        if (!"DOCTOR".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Doctor access required");
        }
        return user;
    }

    public AppUser requirePatient(String authorizationHeader) {
        AppUser user = authenticate(authorizationHeader);
        if (!"PATIENT".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient access required");
        }
        return user;
    }

    public AppUser requirePatientOrDoctorForPatient(String authorizationHeader, String patientId) {
        AppUser user = authenticate(authorizationHeader);
        if ("DOCTOR".equals(user.getRole())) {
            return user;
        }
        if ("PATIENT".equals(user.getRole()) && patientId.equals(user.getPatientId())) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can view only your own patient data");
    }

    public LoginResponse toResponse(AppUser user) {
        return new LoginResponse(user.getUserId(), user.getName(), user.getRole(), user.getPatientId());
    }

    private String decodeBasicHeader(String base64Text) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Text);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login header");
        }
    }
}
