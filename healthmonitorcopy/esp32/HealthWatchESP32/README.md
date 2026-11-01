# HealthWatch ESP32-C3 Sketch

Arduino sketch for sending ESP32-C3 Super Mini sensor readings to the HealthWatch backend.

This sketch is for:

- ESP32-C3 Super Mini
- MPU6050
- MAX30100
- SH1106 128x64 OLED
- No button, no buzzer

## Backend URL

Configure the backend endpoint in `HealthWatchESP32.ino`:

```text
http://<YOUR_BACKEND_IP>:8081/api/health/reading
```
or your deployed cloud backend endpoint (e.g. Render / Railway).

The backend response includes `sendAlertToDevice`. When it is `true`, the alert is shown on the OLED and the onboard LED flashes.

## Arduino IDE Setup

Install:

- ESP32 board support, with board set to `ESP32C3 Dev Module` or your matching Super Mini board
- ArduinoJson library
- U8g2 library
- Adafruit MPU6050 library
- Adafruit Unified Sensor library
- MAX30100lib / MAX30100 PulseOximeter library

Update these values in `HealthWatchESP32.ino`:

```cpp
const char* WIFI_SSID = "YOUR_WIFI_NAME";
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD";
```

## Wiring

Default pins in this sketch:

```text
GPIO 8 -> I2C SDA
GPIO 9 -> I2C SCL
GPIO 2 -> onboard/status LED fallback used by this sketch
3V3     -> sensor/display VCC
GND     -> common ground
```

ESP32-C3 I2C pins are configurable. If your Super Mini board is already wired to different GPIOs, change `I2C_SDA` and `I2C_SCL` in `HealthWatchESP32.ino`. GPIO 8 is used for SDA here, so do not also use GPIO 8 as the status LED.

The sketch scans I2C at boot. Open Serial Monitor at `115200` baud and check for:

```text
MPU6050  -> 0x68
MAX30100 -> 0x57
OLED     -> 0x3C or 0x3D
```

If only `0x68` appears, the MPU6050 is wired correctly but the OLED/MAX30100 are not on the same SDA/SCL bus, not powered, or have a module-level pullup issue.

## Sensor Integration

The sketch reads real MPU6050 acceleration/temperature and MAX30100 BPM/SpO2.

Expected units:

```text
xAccel, yAccel, zAccel -> acceleration in g
heartRate             -> BPM
spo2                  -> percent
temperature           -> Celsius
stepCount             -> steps
```

Do not send `patientId` from ESP32. The backend maps `ESP32-DEMO01` to the correct patient.
