package com.healthmonitor.controller;

import com.healthmonitor.dto.ChatRequest;
import com.healthmonitor.dto.ChatResponse;
import com.healthmonitor.model.AppUser;
import com.healthmonitor.service.AuthService;
import com.healthmonitor.service.ChatbotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final AuthService authService;
    private final ChatbotService chatbotService;

    @PostMapping("/message")
    public ChatResponse message(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody ChatRequest request) {
        AppUser patient = authService.requirePatient(authorization);
        return new ChatResponse(chatbotService.answer(patient.getPatientId(), request.getMessage()));
    }
}
