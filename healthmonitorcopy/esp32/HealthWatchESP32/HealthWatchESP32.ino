/*
 * ============================================================================
 * HealthWatchPro - ESP32 Firmware
 * ============================================================================
 * Based on the requested UI/sensor flow, adapted to the HealthWatch backend.
 *
 * Backend endpoint:
 *   POST http://YOUR_BACKEND_HOST:8081/api/health/reading
 *   (or https://your-backend-service.onrender.com/api/health/reading)
 *
 * Backend JSON fields:
 *   deviceId, xAccel, yAccel, zAccel, heartRate, spo2,
 *   temperature, stepCount, timestamp
 *
 * Sensors:
 *   MPU6050  - acceleration and chip temperature
 *   MAX30100 - heart rate and SpO2
 *   SH1106   - 128x64 OLED
 *
 * Note:
 *   MPU6050 temperature is chip temperature, not body temperature. The backend
 *   validates temperature as 30.0..45.0 C, so this sketch sends a neutral
 *   body-temperature fallback until you add a real body temperature sensor.
 * ============================================================================
 */

#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <U8g2lib.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <MAX30100_PulseOximeter.h>
#include <math.h>
#include <time.h>

// ============================================================================
// CONFIGURATION
// ============================================================================

// WiFi
const char* WIFI_SSID     = "YOUR_WIFI_SSID";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";

// Backend
// Replace with your backend URL (e.g. http://192.168.1.100:8081/api/health/reading or your cloud host)
const char* READING_URL = "http://YOUR_BACKEND_IP:8081/api/health/reading";
const char* DEVICE_ID   = "ESP32-DEMO01";

// Device
const char* DEVICE_NAME = "HealthWatchPro";

// ESP32-C3 Super Mini commonly uses GPIO 8/9 for this wiring.
// If your board is wired like the sample requirement, change these to 21/22.
#define I2C_SDA 8
#define I2C_SCL 9

#define BUTTON_PIN 15

#define LED_PIN  10
#define LED_ON   HIGH
#define LED_OFF  LOW

#define DATA_SEND_INTERVAL_MS     2000UL
#define WIFI_CHECK_INTERVAL_MS    5000UL
#define WIFI_BLINK_INTERVAL_MS     500UL
#define DISPLAY_REFRESH_MS         250UL
#define ALERT_DISPLAY_MS          3000UL
#define PULSE_REPORT_MS           3000UL
#define PULSE_NO_SIGNAL_MS       15000UL
#define PULSE_REINIT_MS          30000UL
#define SCREEN_ROTATE_MS          5000UL

#define STEP_THRESHOLD_MS2       11.0f
#define STEP_DEBOUNCE_MS         300UL

#define FREEFALL_MAG_THRESHOLD    5.2f
#define FREEFALL_DURATION_MS      300UL
#define IMPACT_MAG_THRESHOLD     15.5f
#define DIRECT_IMPACT_THRESHOLD  22.0f

const float GRAVITY_MS2 = 9.80665f;
const float BACKEND_TEMP_FALLBACK_C = 36.7f;
const int   BACKEND_SPO2_FALLBACK = 98;

const uint8_t MPU6050_ADDR     = 0x68;
const uint8_t MPU6050_ALT_ADDR = 0x69;
const uint8_t MAX30100_ADDR    = 0x57;
const uint8_t OLED_ADDR_3C     = 0x3C;
const uint8_t OLED_ADDR_3D     = 0x3D;

// ============================================================================
// HARDWARE
// ============================================================================

U8G2_SH1106_128X64_NONAME_F_HW_I2C u8g2(U8G2_R0, U8X8_PIN_NONE);

Adafruit_MPU6050 mpu;
PulseOximeter    pox;

// ============================================================================
// GLOBAL STATE
// ============================================================================

struct SensorData {
  float accXG = 0.0f;
  float accYG = 0.0f;
  float accZG = 1.0f;
  float magnitudeMs2 = 9.8f;
  float bpm = 0.0f;
  float spo2 = 0.0f;
  float mpuTempC = 0.0f;
  long steps = 0;
};

struct BackendAlert {
  bool active = false;
  bool anyAlert = false;
  bool fallAlert = false;
  bool sendToDevice = false;
  String type = "";
  String message = "";
  String severity = "OK";
  unsigned long displayStart = 0;
};

enum Screen { HOME_SCREEN, HEALTH_SCREEN };

SensorData sensorData;
BackendAlert backendAlert;
Screen currentScreen = HOME_SCREEN;

bool wifiConnected = false;
bool wifiIconBlink = false;
bool timeSynced = false;

bool mpuDetected = false;
bool maxDetected = false;
bool oledDetected = false;
bool mpuReady = false;
bool maxReady = false;
bool oledReady = false;

bool stepDetected = false;
bool fallDetectedLocal = false;
bool fallPending = false;
bool displayNeedsUpdate = true;

uint8_t oledAddress = OLED_ADDR_3C;

bool ledState = false;
bool alertFlashOn = false;
int alertFlashCount = 0;

float bpmSum = 0.0f;
int bpmCount = 0;
float lastRawHr = 0.0f;
float lastRawSpo2 = 0.0f;

unsigned long measureStart = 0;
unsigned long lastStepTime = 0;
unsigned long freeFallStart = 0;
unsigned long fallDisplayStart = 0;
unsigned long lastButtonPress = 0;
unsigned long lastDataSend = 0;
unsigned long lastWiFiCheck = 0;
unsigned long lastWiFiBlink = 0;
unsigned long lastDisplayRefresh = 0;
unsigned long lastPulseReport = 0;
unsigned long lastValidPulseAt = 0;
unsigned long lastPoxReinit = 0;
unsigned long ledOffAt = 0;
unsigned long alertFlashNext = 0;

// ============================================================================
// FORWARD DECLARATIONS
// ============================================================================

void scanI2CDevices();
void initializeDisplay();
bool initializeSensors();
void connectWiFiAsync();
void manageWiFiConnection();
void syncTimeOnce();
void servicePulseOximeter();
void servicePulseOximeterFor(unsigned long durationMs);
void reinitPulseOximeter();
bool pulseSignalActive();
void updateSensorData();
void updateSteps();
void detectFallOffline();
void triggerFallAlert();
void handleButtonPress();
void sendRawDataToBackend();
void parseBackendResponse(const String& json);
void appendJsonEscaped(String& out, const char* text);
bool jsonBool(const String& json, const char* key, bool fallback);
String jsonString(const String& json, const char* key, const char* fallback);
String currentEpochSeconds();
int backendHeartRate();
int backendSpo2();
float backendTemperatureC();
float round1(float value);
float round2(float value);
int roundToInt(float value);
void updateLed();
void startAlertFlash();
void displayWelcome();
void displayError(const char* error);
void displayHome();
void displayHealth();
void displayAlert(const char* alertType, const char* message);
void drawWiFiIcon(int x, int y);
void updateDisplay();
void onBeatDetected();

// ============================================================================
// SETUP
// ============================================================================

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println();
  Serial.println("=== HealthWatchPro Starting ===");

  Wire.begin(I2C_SDA, I2C_SCL);
  Wire.setClock(100000);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LED_OFF);

  pinMode(BUTTON_PIN, INPUT_PULLUP);

  scanI2CDevices();
  initializeDisplay();
  displayWelcome();
  delay(1500);

  if (!initializeSensors()) {
    Serial.println("One or more sensors failed. Device will run in degraded mode.");
    displayError("Sensor warning");
    delay(1200);
  }

  configTime(19800, 0, "pool.ntp.org", "time.nist.gov");
  connectWiFiAsync();

  unsigned long now = millis();
  measureStart = now;
  lastDataSend = now;
  lastWiFiCheck = now;
  lastDisplayRefresh = now;
  lastPulseReport = now;
  lastPoxReinit = now;

  Serial.println("Setup complete!");
}

// ============================================================================
// MAIN LOOP
// ============================================================================

void loop() {
  servicePulseOximeter();

  updateLed();
  updateSensorData();
  handleButtonPress();
  manageWiFiConnection();
  if (!wifiConnected) {
    detectFallOffline();
  }

  unsigned long now = millis();

  if (wifiConnected && (now - lastDataSend >= DATA_SEND_INTERVAL_MS || fallPending)) {
    sendRawDataToBackend();
    lastDataSend = millis();
  } else if (!wifiConnected && now - lastDataSend >= DATA_SEND_INTERVAL_MS) {
    lastDataSend = now;
  }

  if (displayNeedsUpdate || now - lastDisplayRefresh >= DISPLAY_REFRESH_MS) {
    updateDisplay();
    lastDisplayRefresh = now;
  }

  servicePulseOximeterFor(10);
}

// ============================================================================
// INITIALIZATION
// ============================================================================

void scanI2CDevices() {
  Serial.printf("Scanning I2C SDA=%d SCL=%d\n", I2C_SDA, I2C_SCL);

  mpuDetected = false;
  maxDetected = false;
  oledDetected = false;

  bool anyDevice = false;
  for (uint8_t addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      anyDevice = true;
      Serial.printf("  Found: 0x%02X\n", addr);

      if (addr == MPU6050_ADDR || addr == MPU6050_ALT_ADDR) {
        mpuDetected = true;
      }
      if (addr == MAX30100_ADDR) {
        maxDetected = true;
      }
      if (addr == OLED_ADDR_3C || addr == OLED_ADDR_3D) {
        oledDetected = true;
        oledAddress = addr;
      }
    }
  }

  if (!anyDevice) {
    Serial.println("No I2C devices found. Check wiring.");
  }
}

void initializeDisplay() {
  if (!oledDetected) {
    Serial.println("OLED not found.");
    return;
  }

  u8g2.setI2CAddress(oledAddress << 1);
  u8g2.begin();
  u8g2.setFontMode(1);
  oledReady = true;
  Serial.printf("OLED initialized at 0x%02X\n", oledAddress);
}

bool initializeSensors() {
  bool allReady = true;

  if (mpuDetected) {
    mpuReady = mpu.begin();
    if (!mpuReady) mpuReady = mpu.begin(MPU6050_ADDR, &Wire);
    if (!mpuReady) mpuReady = mpu.begin(MPU6050_ALT_ADDR, &Wire);
  }

  if (mpuReady) {
    Serial.println("MPU6050 initialized");
  } else {
    Serial.println("MPU6050 not ready");
    allReady = false;
  }

  if (maxDetected) {
    maxReady = pox.begin();
    if (!maxReady) {
      delay(200);
      maxReady = pox.begin();
    }
  }

  if (maxReady) {
    pox.setIRLedCurrent(MAX30100_LED_CURR_50MA);
    pox.setOnBeatDetectedCallback(onBeatDetected);
    Serial.println("MAX30100 initialized");
  } else {
    Serial.println("MAX30100 not ready");
    allReady = false;
  }

  return allReady;
}

void onBeatDetected() {
  lastValidPulseAt = millis();
  displayNeedsUpdate = true;
  Serial.println("Beat!");
}

// ============================================================================
// DISPLAY
// ============================================================================

void displayWelcome() {
  if (!oledReady) return;

  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_logisoso24_tr);
  u8g2.drawStr(5, 34, "Health");
  u8g2.setFont(u8g2_font_6x12_tr);
  u8g2.drawStr(20, 52, DEVICE_NAME);
  u8g2.sendBuffer();
}

void displayError(const char* error) {
  if (!oledReady) return;

  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_6x12_tr);
  u8g2.drawStr(10, 28, "WARNING");
  u8g2.drawStr(10, 44, error);
  u8g2.sendBuffer();
}

void displayHome() {
  if (!oledReady) return;

  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_6x12_tr);

  time_t nowTime;
  struct tm timeinfo;
  time(&nowTime);
  localtime_r(&nowTime, &timeinfo);

  char timeStr[8] = "--:--";
  char dateStr[12] = "--";
  if (timeSynced && timeinfo.tm_year > 100) {
    strftime(timeStr, sizeof(timeStr), "%H:%M", &timeinfo);
    strftime(dateStr, sizeof(dateStr), "%d %b", &timeinfo);
  }

  u8g2.drawStr(0, 10, timeStr);
  u8g2.drawStr(52, 10, dateStr);
  drawWiFiIcon(112, 0);
  u8g2.drawLine(0, 14, 128, 14);

  u8g2.setFont(u8g2_font_logisoso16_tr);
  char bpmStr[16];
  if (pulseSignalActive() && sensorData.bpm > 0) {
    snprintf(bpmStr, sizeof(bpmStr), "%.0f", sensorData.bpm);
  } else {
    snprintf(bpmStr, sizeof(bpmStr), "--");
  }
  u8g2.drawStr(5, 38, bpmStr);

  u8g2.setFont(u8g2_font_6x12_tr);
  u8g2.drawStr(48, 32, "BPM");

  char spo2Str[16];
  if (pulseSignalActive() && sensorData.spo2 > 0) {
    snprintf(spo2Str, sizeof(spo2Str), "%.0f%%", sensorData.spo2);
  } else {
    snprintf(spo2Str, sizeof(spo2Str), "--");
  }
  u8g2.drawStr(5, 53, spo2Str);
  u8g2.drawStr(48, 52, "SpO2");

  char tempStr[16];
  snprintf(tempStr, sizeof(tempStr), "%.1fC", backendTemperatureC());
  u8g2.drawStr(78, 32, tempStr);
  u8g2.drawStr(78, 45, "Temp");

  char stepStr[16];
  snprintf(stepStr, sizeof(stepStr), "%ld", sensorData.steps);
  u8g2.drawStr(78, 58, stepStr);
  u8g2.drawStr(0, 64, "[HOME]");

  if (maxReady && !pulseSignalActive()) {
    u8g2.drawStr(28, 64, "Place finger");
  }

  u8g2.sendBuffer();
}

void displayHealth() {
  if (!oledReady) return;

  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_6x12_tr);

  u8g2.drawStr(0, 10, "HEALTH DATA");
  u8g2.drawLine(0, 14, 128, 14);

  char line[32];
  snprintf(line, sizeof(line), "BPM: %.0f", sensorData.bpm);
  u8g2.drawStr(5, 25, line);

  snprintf(line, sizeof(line), "SpO2: %.0f%%", sensorData.spo2);
  u8g2.drawStr(5, 35, line);

  snprintf(line, sizeof(line), "Body Temp: %.1fC", backendTemperatureC());
  u8g2.drawStr(5, 45, line);

  snprintf(line, sizeof(line), "MPU Temp: %.1fC", sensorData.mpuTempC);
  u8g2.drawStr(5, 55, line);

  snprintf(line, sizeof(line), "Acc: %.2f g", sensorData.magnitudeMs2 / GRAVITY_MS2);
  u8g2.drawStr(5, 64, line);

  u8g2.sendBuffer();
}

void displayAlert(const char* alertType, const char* message) {
  if (!oledReady) return;

  u8g2.clearBuffer();
  u8g2.setFont(u8g2_font_logisoso24_tr);
  u8g2.drawStr(12, 34, alertType);
  u8g2.setFont(u8g2_font_6x12_tr);
  u8g2.drawStr(5, 54, message);
  u8g2.sendBuffer();
}

void drawWiFiIcon(int x, int y) {
  if (wifiConnected || wifiIconBlink) {
    u8g2.setFont(u8g2_font_6x12_tr);
    u8g2.drawStr(x, y + 10, wifiConnected ? "W" : "...");
  }
}

void updateDisplay() {
  if (!oledReady) return;

  unsigned long now = millis();

  if (backendAlert.active) {
    displayAlert(backendAlert.type.c_str(), backendAlert.message.c_str());
    if (now - backendAlert.displayStart > ALERT_DISPLAY_MS) {
      backendAlert.active = false;
    }
    return;
  }

  if (fallDetectedLocal) {
    displayAlert("FALL!", "Local Detection");
    if (now - fallDisplayStart > ALERT_DISPLAY_MS) {
      fallDetectedLocal = false;
    }
    return;
  }

  if (currentScreen == HOME_SCREEN) {
    displayHome();
  } else {
    displayHealth();
  }

  displayNeedsUpdate = false;
}

// ============================================================================
// SENSOR FUNCTIONS
// ============================================================================

void servicePulseOximeter() {
  if (maxReady) {
    pox.update();
  }
}

void servicePulseOximeterFor(unsigned long durationMs) {
  unsigned long start = millis();
  do {
    servicePulseOximeter();
    delay(1);
  } while (millis() - start < durationMs);
}

bool pulseSignalActive() {
  return lastValidPulseAt > 0 && millis() - lastValidPulseAt < PULSE_NO_SIGNAL_MS;
}

void reinitPulseOximeter() {
  if (!maxDetected) return;

  Serial.println("Reinitializing MAX30100...");
  maxReady = pox.begin();
  if (maxReady) {
    pox.setIRLedCurrent(MAX30100_LED_CURR_50MA);
    pox.setOnBeatDetectedCallback(onBeatDetected);
    Serial.println("MAX30100 reinit OK");
  } else {
    Serial.println("MAX30100 reinit failed");
  }
}

void updateSensorData() {
  unsigned long now = millis();

  if (maxReady) {
    lastRawHr = pox.getHeartRate();
    lastRawSpo2 = pox.getSpO2();

    if (lastRawHr > 40.0f && lastRawHr < 180.0f) {
      bpmSum += lastRawHr;
      bpmCount++;
      lastValidPulseAt = now;
    }

    if (lastRawSpo2 > 80.0f && lastRawSpo2 <= 100.0f) {
      sensorData.spo2 = lastRawSpo2;
      lastValidPulseAt = now;
    }

    if (now - measureStart > 6000) {
      if (bpmCount > 0) {
        sensorData.bpm = bpmSum / bpmCount;
        if (sensorData.bpm < 60.0f) sensorData.bpm = 60.0f;
        if (sensorData.bpm > 140.0f) sensorData.bpm = 140.0f;
      } else if (!pulseSignalActive()) {
        sensorData.bpm = 0.0f;
        sensorData.spo2 = 0.0f;
      }

      bpmSum = 0.0f;
      bpmCount = 0;
      measureStart = now;
      displayNeedsUpdate = true;
    }

    if (now - lastPulseReport >= PULSE_REPORT_MS) {
      Serial.printf("Pulse raw HR:%.1f SpO2:%.1f | display HR:%.0f SpO2:%.0f | %s\n",
        lastRawHr,
        lastRawSpo2,
        sensorData.bpm,
        sensorData.spo2,
        pulseSignalActive() ? "signal OK" : "place finger");
      lastPulseReport = now;
    }

    if (!pulseSignalActive() && now - lastPoxReinit >= PULSE_REINIT_MS) {
      reinitPulseOximeter();
      lastPoxReinit = now;
    }
  } else {
    sensorData.bpm = 0.0f;
    sensorData.spo2 = 0.0f;
  }

  if (mpuReady) {
    sensors_event_t a, g, temp;
    mpu.getEvent(&a, &g, &temp);

    sensorData.accXG = a.acceleration.x / GRAVITY_MS2;
    sensorData.accYG = a.acceleration.y / GRAVITY_MS2;
    sensorData.accZG = a.acceleration.z / GRAVITY_MS2;
    sensorData.mpuTempC = temp.temperature;
    sensorData.magnitudeMs2 = sqrtf(
      a.acceleration.x * a.acceleration.x +
      a.acceleration.y * a.acceleration.y +
      a.acceleration.z * a.acceleration.z);
  }

  updateSteps();
}

void updateSteps() {
  unsigned long now = millis();

  if (sensorData.magnitudeMs2 > STEP_THRESHOLD_MS2 &&
      !stepDetected &&
      now - lastStepTime > STEP_DEBOUNCE_MS) {
    sensorData.steps++;
    stepDetected = true;
    lastStepTime = now;
    displayNeedsUpdate = true;
  }

  if (sensorData.magnitudeMs2 < 9.0f) {
    stepDetected = false;
  }
}

// ============================================================================
// WIFI FUNCTIONS
// ============================================================================

void connectWiFiAsync() {
  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    Serial.println("WiFi already connected");
    return;
  }

  Serial.print("Connecting to WiFi: ");
  Serial.println(WIFI_SSID);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  wifiConnected = false;
}

void manageWiFiConnection() {
  unsigned long now = millis();

  if (now - lastWiFiBlink > WIFI_BLINK_INTERVAL_MS) {
    wifiIconBlink = !wifiIconBlink;
    lastWiFiBlink = now;
    if (!wifiConnected) displayNeedsUpdate = true;
  }

  if (now - lastWiFiCheck < WIFI_CHECK_INTERVAL_MS) {
    return;
  }

  lastWiFiCheck = now;

  if (WiFi.status() == WL_CONNECTED) {
    if (!wifiConnected) {
      wifiConnected = true;
      wifiIconBlink = false;
      displayNeedsUpdate = true;
      Serial.println("WiFi connected");
      Serial.print("IP: ");
      Serial.println(WiFi.localIP());
      syncTimeOnce();
    }
  } else {
    if (wifiConnected) {
      wifiConnected = false;
      displayNeedsUpdate = true;
      Serial.println("WiFi disconnected");
    }

    Serial.println("Retrying WiFi connection...");
    WiFi.disconnect();
    delay(50);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  }
}

void syncTimeOnce() {
  if (timeSynced) return;

  struct tm t;
  if (getLocalTime(&t, 100)) {
    timeSynced = true;
    displayNeedsUpdate = true;
    Serial.println("NTP OK");
  } else {
    Serial.println("NTP pending");
  }
}

// ============================================================================
// DATA TRANSMISSION
// ============================================================================

void sendRawDataToBackend() {
  if (!wifiConnected) return;

  int payloadHeartRate = backendHeartRate();
  int payloadSpo2 = backendSpo2();
  float payloadTempC = backendTemperatureC();

  String json;
  json.reserve(256);
  json += "{\"deviceId\":\"";
  appendJsonEscaped(json, DEVICE_ID);
  json += "\",\"xAccel\":";
  json += String(round2(sensorData.accXG), 2);
  json += ",\"yAccel\":";
  json += String(round2(sensorData.accYG), 2);
  json += ",\"zAccel\":";
  json += String(round2(sensorData.accZG), 2);
  json += ",\"heartRate\":";
  json += String(payloadHeartRate);
  json += ",\"spo2\":";
  json += String(payloadSpo2);
  json += ",\"temperature\":";
  json += String(payloadTempC, 1);
  json += ",\"stepCount\":";
  json += String(sensorData.steps);

  String epoch = currentEpochSeconds();
  if (epoch.length()) {
    json += ",\"timestamp\":\"";
    appendJsonEscaped(json, epoch.c_str());
    json += "\"";
  }
  json += "}";

  Serial.print("POST ");
  Serial.println(READING_URL);
  Serial.println(json);
  Serial.printf("Payload HR:%d SpO2:%d Temp:%.1f | raw HR:%.1f SpO2:%.1f MPU temp:%.1f\n",
    payloadHeartRate,
    payloadSpo2,
    payloadTempC,
    lastRawHr,
    lastRawSpo2,
    sensorData.mpuTempC);

  WiFiClientSecure client;
  client.setInsecure();

  HTTPClient http;
  http.setTimeout(12000);

  if (!http.begin(client, READING_URL)) {
    Serial.println("HTTP begin failed");
    return;
  }

  http.addHeader("Content-Type", "application/json");

  int httpResponseCode = http.POST(json);
  String response = http.getString();
  http.end();

  Serial.print("POST Response: ");
  Serial.println(httpResponseCode);
  Serial.println(response);

  if (httpResponseCode >= 200 && httpResponseCode < 300) {
    fallPending = false;
    parseBackendResponse(response);
  } else {
    backendAlert.active = true;
    backendAlert.type = "HTTP";
    backendAlert.message = "Post failed";
    backendAlert.severity = "ERROR";
    backendAlert.displayStart = millis();
    displayNeedsUpdate = true;
  }
}

void parseBackendResponse(const String& json) {
  backendAlert.sendToDevice = jsonBool(json, "sendAlertToDevice", false);
  backendAlert.anyAlert = jsonBool(json, "anyAlert", false);
  backendAlert.fallAlert = jsonBool(json, "fallAlert", false);
  backendAlert.severity = jsonString(json, "severity", "OK");
  backendAlert.type = jsonString(json, "deviceAlertType", "");
  backendAlert.message = jsonString(json, "deviceAlertMessage", "");

  if (!backendAlert.type.length()) {
    backendAlert.type = backendAlert.fallAlert ? "FALL!" : "ALERT!";
  }

  if (!backendAlert.message.length()) {
    backendAlert.message = jsonString(json, "message", "Check readings");
  }

  if (backendAlert.sendToDevice || backendAlert.anyAlert) {
    backendAlert.active = true;
    backendAlert.displayStart = millis();
    displayNeedsUpdate = true;
    startAlertFlash();
    Serial.println("Backend alert received");
  } else {
    backendAlert.active = false;
    digitalWrite(LED_PIN, LED_OFF);
    ledState = false;
    displayNeedsUpdate = true;
  }
}

// ============================================================================
// OFFLINE FALL DETECTION
// ============================================================================

void detectFallOffline() {
  if (!mpuReady) return;

  unsigned long now = millis();

  if (sensorData.magnitudeMs2 < FREEFALL_MAG_THRESHOLD) {
    if (!freeFallStart) freeFallStart = now;
  } else {
    if (freeFallStart &&
        now - freeFallStart > FREEFALL_DURATION_MS &&
        sensorData.magnitudeMs2 > IMPACT_MAG_THRESHOLD) {
      triggerFallAlert();
    }
    freeFallStart = 0;
  }

  if (sensorData.magnitudeMs2 > DIRECT_IMPACT_THRESHOLD) {
    triggerFallAlert();
  }
}

void triggerFallAlert() {
  if (!fallDetectedLocal) {
    fallDetectedLocal = true;
    fallPending = true;
    fallDisplayStart = millis();
    displayNeedsUpdate = true;
    startAlertFlash();
    Serial.println("LOCAL FALL DETECTED");
  }
}

// ============================================================================
// INPUT HANDLING
// ============================================================================

void handleButtonPress() {
  if (digitalRead(BUTTON_PIN) == LOW) {
    if (millis() - lastButtonPress > 500) {
      currentScreen = (currentScreen == HOME_SCREEN) ? HEALTH_SCREEN : HOME_SCREEN;
      lastButtonPress = millis();
      displayNeedsUpdate = true;
      Serial.print("Screen toggled to: ");
      Serial.println(currentScreen == HOME_SCREEN ? "HOME" : "HEALTH");
    }
  }
}

// ============================================================================
// LED
// ============================================================================

void updateLed() {
  unsigned long now = millis();

  if (ledState && ledOffAt && now >= ledOffAt) {
    ledState = false;
    ledOffAt = 0;
    digitalWrite(LED_PIN, LED_OFF);
  }

  if (alertFlashCount > 0 && now >= alertFlashNext) {
    if (alertFlashOn) {
      digitalWrite(LED_PIN, LED_OFF);
      ledState = false;
      alertFlashOn = false;
      alertFlashCount--;
      alertFlashNext = now + 120;
    } else {
      digitalWrite(LED_PIN, LED_ON);
      ledState = true;
      alertFlashOn = true;
      alertFlashNext = now + 150;
    }
  }
}

void startAlertFlash() {
  alertFlashCount = 3;
  alertFlashOn = false;
  alertFlashNext = millis();
}

// ============================================================================
// HELPERS
// ============================================================================

static bool isJsonSpace(char c) {
  return c == ' ' || c == '\n' || c == '\r' || c == '\t';
}

static int jsonValueStart(const String& json, const char* key) {
  String needle = "\"";
  needle += key;
  needle += "\"";

  int keyAt = json.indexOf(needle);
  if (keyAt < 0) return -1;

  int colon = json.indexOf(':', keyAt + needle.length());
  if (colon < 0) return -1;

  int pos = colon + 1;
  while (pos < json.length() && isJsonSpace(json[pos])) pos++;
  return pos;
}

bool jsonBool(const String& json, const char* key, bool fallback) {
  int pos = jsonValueStart(json, key);
  if (pos < 0) return fallback;
  if (json.startsWith("true", pos)) return true;
  if (json.startsWith("false", pos)) return false;
  return fallback;
}

String jsonString(const String& json, const char* key, const char* fallback) {
  int pos = jsonValueStart(json, key);
  if (pos < 0 || pos >= json.length() || json[pos] != '"') {
    return String(fallback);
  }

  String out;
  bool escaped = false;
  for (int i = pos + 1; i < json.length(); i++) {
    char c = json[i];
    if (escaped) {
      if (c == 'n') out += '\n';
      else if (c == 'r') out += '\r';
      else if (c == 't') out += '\t';
      else out += c;
      escaped = false;
    } else if (c == '\\') {
      escaped = true;
    } else if (c == '"') {
      return out;
    } else {
      out += c;
    }
  }

  return String(fallback);
}

void appendJsonEscaped(String& out, const char* text) {
  while (*text) {
    char c = *text++;
    if (c == '"' || c == '\\') {
      out += '\\';
      out += c;
    } else if (c == '\n') {
      out += "\\n";
    } else if (c == '\r') {
      out += "\\r";
    } else if (c == '\t') {
      out += "\\t";
    } else {
      out += c;
    }
  }
}

String currentEpochSeconds() {
  time_t nowTime = time(nullptr);
  return (nowTime < 1700000000) ? "" : String((unsigned long)nowTime);
}

int backendHeartRate() {
  if (pulseSignalActive() && sensorData.bpm >= 40.0f && sensorData.bpm <= 180.0f) {
    return roundToInt(sensorData.bpm);
  }
  return 0;
}

int backendSpo2() {
  if (pulseSignalActive() && sensorData.spo2 >= 80.0f && sensorData.spo2 <= 100.0f) {
    return roundToInt(sensorData.spo2);
  }
  return BACKEND_SPO2_FALLBACK;
}

float backendTemperatureC() {
  return BACKEND_TEMP_FALLBACK_C;
}

float round1(float value) {
  return roundf(value * 10.0f) / 10.0f;
}

float round2(float value) {
  return roundf(value * 100.0f) / 100.0f;
}

int roundToInt(float value) {
  return (value <= 0.0f) ? 0 : (int)roundf(value);
}
