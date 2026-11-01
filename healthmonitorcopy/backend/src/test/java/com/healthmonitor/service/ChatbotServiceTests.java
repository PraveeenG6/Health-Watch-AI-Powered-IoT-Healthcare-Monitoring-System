package com.healthmonitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatbotServiceTests {

    @Test
    void extractsTextFromGeminiCandidateParts() {
        ChatbotService service = new ChatbotService(null, new ObjectMapper());
        String rawResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Drink water." },
                          { "text": "Call a doctor if symptoms worsen." }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ]
                }
                """;

        String reply = ReflectionTestUtils.invokeMethod(service, "extractResponseText", rawResponse);

        assertEquals("Drink water.\nCall a doctor if symptoms worsen.", reply);
    }
}
