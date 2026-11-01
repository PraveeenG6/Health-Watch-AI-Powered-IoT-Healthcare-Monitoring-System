package com.healthmonitor.service.ai;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DL4J fall-detection model trained from CSV rows.
 *
 * CSV format:
 * date,time,xacc,yacc,zacc,heartrate,spo2,stepcount,temp,falldetection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthAiService {

    private static final int FEATURE_COUNT = 8;
    private static final double EPSILON = 1.0e-8;

    private final ResourceLoader resourceLoader;

    @Value("${app.ai.fall-dataset:classpath:data/fall-detection-training.csv}")
    private String fallDatasetLocation;

    @Value("${app.ai.training-epochs:250}")
    private int trainingEpochs;

    @Value("${app.ai.fall-threshold:0.70}")
    private double fallThreshold;

    @Value("${app.alert.heart-rate.high:110}")
    private double hrHigh;

    @Value("${app.alert.heart-rate.low:45}")
    private double hrLow;

    @Value("${app.alert.spo2.low:94}")
    private double spo2Low;

    @Value("${app.alert.temperature.high:38.5}")
    private double tempHigh;

    @Value("${app.alert.temperature.low:35.0}")
    private double tempLow;

    private MultiLayerNetwork model;
    private double[] featureMeans = new double[FEATURE_COUNT];
    private double[] featureStdDevs = new double[FEATURE_COUNT];

    @PostConstruct
    void trainFallDetectionModel() {
        try {
            TrainingRows trainingRows = loadTrainingRows();
            if (trainingRows.features().size() < 4 || !trainingRows.hasBothClasses()) {
                log.warn("Fall dataset {} does not contain enough labelled normal/fall rows. Using heuristic fallback.",
                        fallDatasetLocation);
                return;
            }

            fitScaler(trainingRows.features());
            INDArray features = Nd4j.create(normalise(trainingRows.features()));
            INDArray labels = Nd4j.create(trainingRows.labels());
            DataSet trainingData = new DataSet(features, labels);
            trainingData.shuffle(10);

            MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder()
                    .seed(42)
                    .updater(new Adam(0.005))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(new DenseLayer.Builder()
                            .nIn(FEATURE_COUNT)
                            .nOut(24)
                            .activation(Activation.RELU)
                            .build())
                    .layer(new DenseLayer.Builder()
                            .nIn(24)
                            .nOut(12)
                            .activation(Activation.RELU)
                            .build())
                    .layer(new OutputLayer.Builder(LossFunctions.LossFunction.XENT)
                            .nIn(12)
                            .nOut(1)
                            .activation(Activation.SIGMOID)
                            .build())
                    .build();

            MultiLayerNetwork trainedModel = new MultiLayerNetwork(configuration);
            trainedModel.init();
            for (int epoch = 0; epoch < trainingEpochs; epoch++) {
                trainedModel.fit(trainingData);
            }
            model = trainedModel;
            log.info("DL4J fall-detection model trained from {} with {} rows",
                    fallDatasetLocation, trainingRows.features().size());
        } catch (Exception e) {
            log.warn("Could not train DL4J fall-detection model from {}. Using heuristic fallback. Error: {}",
                    fallDatasetLocation, e.getMessage());
        }
    }

    public synchronized HealthAiResult analyze(
            double xAccel,
            double yAccel,
            double zAccel,
            double heartRate,
            double spo2,
            double temperature) {
        double[] features = extractFeatures(xAccel, yAccel, zAccel, heartRate, spo2, temperature);
        double fallProbability = model == null
                ? heuristicFallProbability(xAccel, yAccel, zAccel)
                : model.output(Nd4j.create(new double[][]{normalise(features)})).getDouble(0, 0);

        fallProbability = clamp(fallProbability);
        boolean fallDetected = fallProbability >= fallThreshold;
        boolean abnormalVitals = isAbnormalVitals(heartRate, spo2, temperature);
        String riskLevel = riskLevel(fallDetected, abnormalVitals);
        double confidence = fallDetected ? fallProbability : 1.0 - fallProbability;

        return new HealthAiResult(
                riskLevel,
                round(confidence),
                buildMessage(fallDetected, fallProbability, abnormalVitals),
                fallDetected,
                round(fallProbability),
                fallThreshold);
    }

    private TrainingRows loadTrainingRows() throws IOException {
        Resource resource = resourceLoader.getResource(fallDatasetLocation);
        if (!resource.exists()) {
            throw new IOException("Dataset not found");
        }

        List<double[]> features = new ArrayList<>();
        List<double[]> labels = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Dataset is empty");
            }

            Map<String, Integer> header = parseHeader(headerLine);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(",", -1);
                try {
                    double xAccel = parseDouble(columns, header, "xacc");
                    double yAccel = parseDouble(columns, header, "yacc");
                    double zAccel = parseDouble(columns, header, "zacc");
                    double heartRate = parseDouble(columns, header, "heartrate");
                    double spo2 = parseDouble(columns, header, "spo2");
                    double temperature = parseDouble(columns, header, "temp");
                    boolean fall = parseFallLabel(columns, header);

                    features.add(extractFeatures(xAccel, yAccel, zAccel, heartRate, spo2, temperature));
                    labels.add(new double[]{fall ? 1.0 : 0.0});
                } catch (RuntimeException e) {
                    log.warn("Skipping invalid fall-training row {}: {}", lineNumber, e.getMessage());
                }
            }
        }

        return new TrainingRows(features, labels.toArray(double[][]::new));
    }

    private Map<String, Integer> parseHeader(String headerLine) {
        String[] columns = headerLine.split(",", -1);
        Map<String, Integer> header = new HashMap<>();
        for (int i = 0; i < columns.length; i++) {
            header.put(columns[i].trim().toLowerCase(Locale.ROOT), i);
        }
        return header;
    }

    private double parseDouble(String[] columns, Map<String, Integer> header, String name) {
        Integer index = header.get(name);
        if (index == null || index >= columns.length) {
            throw new IllegalArgumentException("Missing column " + name);
        }
        return Double.parseDouble(columns[index].trim());
    }

    private boolean parseFallLabel(String[] columns, Map<String, Integer> header) {
        Integer index = header.get("falldetection");
        if (index == null || index >= columns.length) {
            throw new IllegalArgumentException("Missing column falldetection");
        }
        String value = columns[index].trim().toLowerCase(Locale.ROOT);
        return "1".equals(value) || "true".equals(value) || "yes".equals(value) || "fall".equals(value);
    }

    private double[] extractFeatures(
            double xAccel,
            double yAccel,
            double zAccel,
            double heartRate,
            double spo2,
            double temperature) {
        double magnitude = Math.sqrt(xAccel * xAccel + yAccel * yAccel + zAccel * zAccel);
        double gravity = magnitude > 6.0 ? 9.81 : 1.0;
        double magnitudeInGravityUnits = magnitude / gravity;
        double deltaFromGravity = Math.abs(magnitudeInGravityUnits - 1.0);

        return new double[]{
                xAccel,
                yAccel,
                zAccel,
                magnitudeInGravityUnits,
                deltaFromGravity,
                heartRate,
                spo2,
                temperature
        };
    }

    private void fitScaler(List<double[]> rows) {
        featureMeans = new double[FEATURE_COUNT];
        featureStdDevs = new double[FEATURE_COUNT];

        for (double[] row : rows) {
            for (int i = 0; i < FEATURE_COUNT; i++) {
                featureMeans[i] += row[i];
            }
        }
        for (int i = 0; i < FEATURE_COUNT; i++) {
            featureMeans[i] /= rows.size();
        }

        for (double[] row : rows) {
            for (int i = 0; i < FEATURE_COUNT; i++) {
                double delta = row[i] - featureMeans[i];
                featureStdDevs[i] += delta * delta;
            }
        }
        for (int i = 0; i < FEATURE_COUNT; i++) {
            featureStdDevs[i] = Math.sqrt(featureStdDevs[i] / rows.size());
            if (featureStdDevs[i] < EPSILON) {
                featureStdDevs[i] = 1.0;
            }
        }
    }

    private double[][] normalise(List<double[]> rows) {
        double[][] normalised = new double[rows.size()][FEATURE_COUNT];
        for (int row = 0; row < rows.size(); row++) {
            normalised[row] = normalise(rows.get(row));
        }
        return normalised;
    }

    private double[] normalise(double[] row) {
        double[] normalised = new double[FEATURE_COUNT];
        for (int i = 0; i < FEATURE_COUNT; i++) {
            normalised[i] = (row[i] - featureMeans[i]) / featureStdDevs[i];
        }
        return normalised;
    }

    private double heuristicFallProbability(double xAccel, double yAccel, double zAccel) {
        double magnitude = Math.sqrt(xAccel * xAccel + yAccel * yAccel + zAccel * zAccel);
        double magnitudeInGravityUnits = magnitude > 6.0 ? magnitude / 9.81 : magnitude;
        double impactScore = sigmoid((magnitudeInGravityUnits - 2.2) * 3.0);
        double freeFallScore = sigmoid((0.55 - magnitudeInGravityUnits) * 5.0);
        return Math.max(impactScore, freeFallScore * 0.85);
    }

    private boolean isAbnormalVitals(double heartRate, double spo2, double temperature) {
        return heartRate > hrHigh
                || (heartRate > 0 && heartRate < hrLow)
                || spo2 < spo2Low
                || temperature > tempHigh
                || temperature < tempLow;
    }

    private String riskLevel(boolean fallDetected, boolean abnormalVitals) {
        if (fallDetected) {
            return "EMERGENCY";
        }
        if (abnormalVitals) {
            return "CAUTION";
        }
        return "NORMAL";
    }

    private String buildMessage(boolean fallDetected, double probability, boolean abnormalVitals) {
        if (fallDetected) {
            return "Fall likely from accelerometer pattern. Probability: " + round(probability) + ".";
        }
        if (abnormalVitals) {
            return "Vitals crossed configured thresholds. Continue monitoring and review the patient.";
        }
        return "No fall pattern detected and vitals are within configured limits.";
    }

    private double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    public record HealthAiResult(
            String riskLevel,
            double confidence,
            String message,
            boolean fallDetected,
            double fallProbability,
            double fallThreshold) {
    }

    private record TrainingRows(List<double[]> features, double[][] labels) {
        boolean hasBothClasses() {
            boolean normal = false;
            boolean fall = false;
            for (double[] label : labels) {
                if (label[0] >= 0.5) {
                    fall = true;
                } else {
                    normal = true;
                }
            }
            return normal && fall;
        }
    }
}
