package com.healthmonitor.service.ai;

import com.healthmonitor.service.ai.HealthAiService.HealthAiResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthAiServiceTests {

    @Test
    void trainsFromCsvAndScoresFallPatternHigherThanNormalPattern() {
        HealthAiService service = new HealthAiService(new DefaultResourceLoader());
        ReflectionTestUtils.setField(service, "fallDatasetLocation", "classpath:data/fall-detection-training.csv");
        ReflectionTestUtils.setField(service, "trainingEpochs", 80);
        ReflectionTestUtils.setField(service, "fallThreshold", 0.70);
        ReflectionTestUtils.setField(service, "hrHigh", 110.0);
        ReflectionTestUtils.setField(service, "hrLow", 45.0);
        ReflectionTestUtils.setField(service, "spo2Low", 94.0);
        ReflectionTestUtils.setField(service, "tempHigh", 38.5);
        ReflectionTestUtils.setField(service, "tempLow", 35.0);

        service.trainFallDetectionModel();

        HealthAiResult normal = service.analyze(0.02, 0.03, 1.01, 78, 98, 36.7);
        HealthAiResult fall = service.analyze(3.10, -1.30, 0.70, 124, 93, 37.1);

        assertFalse(normal.fallDetected());
        assertTrue(fall.fallDetected());
        assertTrue(fall.fallProbability() > normal.fallProbability());
    }
}
