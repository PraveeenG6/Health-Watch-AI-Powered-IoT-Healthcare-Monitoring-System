package com.healthmonitor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthmonitor.model.HealthReading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotService {

    private static final String DEFAULT_MODEL = "gemini-2.5-flash";
    private static final String GEMINI_API_KEY_HEADER = "x-goog-api-key";
    private static final Pattern GOOGLE_API_KEY_PATTERN = Pattern.compile("AIza[0-9A-Za-z_-]{20,}");
    private static final Pattern API_KEY_QUERY_PATTERN = Pattern.compile("(?i)(key=)[^&\\s]+");

    private static final String HEALTH_ASSISTANT_INSTRUCTIONS = """
            You are HealthWatch AI, a careful health-monitoring assistant for patients.
            Use the latest sensor context when it is provided.
            Give concise, practical health education and monitoring guidance.
            Do not diagnose disease, prescribe medicine, or replace a clinician.
            For falls, head injury, chest pain, severe breathing trouble, fainting, confusion,
            blue lips, SpO2 at or below 90, or rapidly worsening symptoms, tell the user to
            contact emergency medical help immediately.
            If a question is not health related, politely bring the conversation back to health monitoring.
            """;

    private final HealthService healthService;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.builder().build();

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${gemini.api.max-output-tokens:350}")
    private int maxOutputTokens;

    public String answer(String patientId, String message) {
        String apiKey = trimToEmpty(geminiApiKey);
        if (apiKey.isBlank()) {
            return "AI chatbot is not configured. Set GEMINI_API_KEY on the backend server to enable Gemini responses.";
        }

        String userMessage = message == null ? "" : message.trim();
        if (userMessage.isBlank()) {
            return "Please send a health question so I can help.";
        }

        HealthReading latest = healthService.getLatestReading(patientId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", HEALTH_ASSISTANT_INSTRUCTIONS))));
        payload.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", buildInput(latest, userMessage))))));
        payload.put("generationConfig", Map.of("maxOutputTokens", maxOutputTokens));

        try {
            String rawResponse = restClient.post()
                    .uri(generateContentUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(GEMINI_API_KEY_HEADER, apiKey)
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            return extractResponseText(rawResponse);
        } catch (RestClientResponseException e) {
            String errorMessage = geminiErrorMessage(e);
            log.warn("Gemini rejected chatbot request with HTTP {}: {}", e.getStatusCode().value(), errorMessage);
            return errorMessage;
        } catch (RestClientException e) {
            log.warn("Could not reach Gemini API: {}", e.getMessage());
            return "The AI chatbot could not reach Gemini right now. Please try again later.";
        } catch (JsonProcessingException e) {
            log.warn("Could not parse Gemini chatbot response: {}", e.getMessage());
            return "Gemini returned a response the chatbot could not understand. Please try again later.";
        }
    }

    private String generateContentUrl() {
        String model = valueOrDefault(geminiModel, DEFAULT_MODEL);
        String modelPath = model.startsWith("models/") ? model : "models/" + model;
        return valueOrDefault(geminiBaseUrl, "https://generativelanguage.googleapis.com/v1beta")
                .replaceAll("/+$", "") + "/" + modelPath + ":generateContent";
    }

    private String buildInput(HealthReading latest, String userMessage) {
        return """
                Latest patient sensor context:
                %s

                Patient question:
                %s
                """.formatted(buildLatestContext(latest), userMessage);
    }

    private String buildLatestContext(HealthReading latest) {
        if (latest == null) {
            return "No sensor reading is available yet.";
        }

        return """
                Heart rate: %.0f BPM (%s)
                SpO2: %.1f%% (%s)
                Temperature: %.1f C (%s)
                Accelerometer: x=%.3f, y=%.3f, z=%.3f
                Fall detected by model: %s
                Fall probability: %.3f
                AI risk level: %s
                Sensor timestamp: %s
                """.formatted(
                latest.getHeartRate(),
                valueOrUnknown(latest.getHeartRateStatus()),
                latest.getSpo2(),
                valueOrUnknown(latest.getSpo2Status()),
                latest.getTemperature(),
                valueOrUnknown(latest.getTempStatus()),
                latest.getXAccel(),
                latest.getYAccel(),
                latest.getZAccel(),
                latest.isFallDetected() ? "yes" : "no",
                latest.getFallProbability(),
                valueOrUnknown(latest.getAiRiskLevel()),
                latest.getTimestamp() != null ? latest.getTimestamp() : latest.getRecordedAt());
    }

    private String extractResponseText(String rawResponse) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(rawResponse);
        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : root.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                String contentText = part.path("text").asText("");
                if (!contentText.isBlank()) {
                    if (text.length() > 0) {
                        text.append("\n");
                    }
                    text.append(contentText);
                }
            }
        }

        if (text.length() > 0) {
            return text.toString();
        }
        JsonNode blockReason = root.path("promptFeedback").path("blockReason");
        if (blockReason.isTextual() && !blockReason.asText().isBlank()) {
            return "Gemini blocked this request: " + blockReason.asText();
        }
        JsonNode finishReason = root.path("candidates").path(0).path("finishReason");
        if (finishReason.isTextual() && !finishReason.asText().isBlank()) {
            return "Gemini did not return text for this request. Finish reason: " + finishReason.asText();
        }
        JsonNode error = root.path("error").path("message");
        if (error.isTextual() && !error.asText().isBlank()) {
            return "Gemini returned an error: " + error.asText();
        }
        return "Gemini returned an empty response. Please try again.";
    }

    private String geminiErrorMessage(RestClientResponseException exception) {
        String errorText = extractErrorText(exception.getResponseBodyAsString());
        if (errorText.isBlank()) {
            return "Gemini returned HTTP " + exception.getStatusCode().value()
                    + ". Check your API key, model name, quota, and network access.";
        }
        return "Gemini returned HTTP " + exception.getStatusCode().value() + ": " + sanitiseSecret(errorText);
    }

    private String extractErrorText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error").path("message");
            if (error.isTextual() && !error.asText().isBlank()) {
                return error.asText();
            }
            JsonNode message = root.path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return message.asText();
            }
        } catch (JsonProcessingException ignored) {
            return responseBody;
        }
        return responseBody;
    }

    private String sanitiseSecret(String value) {
        String redacted = GOOGLE_API_KEY_PATTERN.matcher(value).replaceAll("[redacted API key]");
        return API_KEY_QUERY_PATTERN.matcher(redacted).replaceAll("$1[redacted API key]");
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        String trimmed = trimToEmpty(value);
        return trimmed.isBlank() ? defaultValue : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
