# HealthWatch Student Health Monitoring System

Simple academic project using Spring Boot, MongoDB, DeepLearning4J, and a plain HTML/CSS/JavaScript frontend.

## Demo Login

The app seeds two MongoDB users when it starts:

| Role | User ID | Password |
| --- | --- | --- |
| PATIENT | `patient1` | `miniproject` |
| DOCTOR | `doctor1` | `miniproject` |

The frontend sends normal HTTP Basic authentication:

```text
Authorization: Basic base64(userId:password)
```

No Spring Security configuration is used. The controllers call `AuthService` directly, which keeps the code easy to explain.

## Folder Structure

```text
frontend/
  index.html
  css/
  js/
    api.js
    auth.js
    patient-dashboard.js
    doctor-dashboard.js
    alerts.js

backend/src/main/java/com/healthmonitor/
  controller/      REST APIs
  dto/             Request and response objects
  model/           MongoDB documents
  repository/      MongoDB repositories
  service/         Business logic
  service/ai/      DL4J fall-detection model
  config/          CORS and demo data
```

## Main Features

- Patient and doctor login from MongoDB users.
- Patient can see only their own readings, alerts, consultations, and chatbot.
- Doctor can view all patients, their readings, alerts, charts, and consultations.
- Chart.js shows heart rate, temperature, and SpO2 history.
- Consultation API stores doctor suggestions with consultation time.
- Smart alerts are created from threshold checks and DL4J fall prediction.
- Patient chatbot uses the Gemini API with latest sensor context.

## Important APIs

### Login

`POST /api/auth/login`

Request:

```json
{
  "userId": "patient1",
  "password": "miniproject"
}
```

Response:

```json
{
  "userId": "patient1",
  "name": "John Doe",
  "role": "PATIENT",
  "patientId": "662f..."
}
```

### Save Sensor Reading

`POST /api/health/reading`

Request:

```json
{
  "deviceId": "ESP32-DEMO01",
  "xAccel": 0.04,
  "yAccel": -0.02,
  "zAccel": 1.02,
  "temperature": 37.2,
  "heartRate": 86,
  "spo2": 98,
  "stepCount": 1200,
  "timestamp": "2026-05-08T10:30:00Z"
}
```

The backend resolves the patient from `deviceId`, so ESP32/Postman requests should normally omit `patientId`. `xAccel`, `yAccel`, and `zAccel` may also be sent as `xacc`, `yacc`, and `zacc`. `stepCount` may also be sent as `steps`. `timestamp` may also be sent as epoch seconds or epoch milliseconds. Fall detection is predicted by the backend DL4J model from accelerometer values; the ESP32 no longer needs to send a `fallDetected` flag.

Response:

```json
{
  "heartRateAlert": false,
  "spo2Alert": false,
  "fallAlert": false,
  "tempAlert": false,
  "aiAlert": false,
  "anyAlert": false,
  "sendAlertToDevice": false,
  "message": "All OK",
  "severity": "OK",
  "aiRiskLevel": "NORMAL",
  "modelFallDetected": false,
  "fallProbability": 0.043
}
```

ESP32 alert handling should use `sendAlertToDevice`. It becomes `true` only when a new abnormal condition creates a new alert, so repeated abnormal readings do not continuously buzz the device while the same alert is already active.

### Patient Dashboard

`GET /api/dashboard/patient/{patientId}`

Returns summary, active alerts, recent readings, and consultations for that patient.

### Doctor Overview

`GET /api/dashboard/doctor/{doctorId}/overview`

Returns all patient cards with latest reading, summary, and alert count.

### Save Consultation

`POST /api/consultations`

Request:

```json
{
  "patientId": "662f...",
  "suggestions": "Drink water, rest, and recheck SpO2 after 30 minutes."
}
```

Response:

```json
{
  "id": "6630...",
  "patientId": "662f...",
  "doctorId": "doctor1",
  "doctorName": "Dr. Robert Smith",
  "consultationTime": "2026-05-01T08:30:00Z",
  "suggestions": "Drink water, rest, and recheck SpO2 after 30 minutes."
}
```

### Patient Chatbot

`POST /api/chatbot/message`

Request:

```json
{
  "message": "How is my oxygen level?"
}
```

Response:

```json
{
  "reply": "Your latest oxygen reading is 98%, which is within the configured normal range. Keep monitoring and seek urgent help if you develop severe breathing trouble or the value falls sharply."
}
```

## DL4J AI Explanation

`HealthAiService` trains a DL4J neural network at startup from:

```text
healthmonitor/src/main/resources/data/fall-detection-training.csv
```

Expected CSV header:

```csv
date,time,xacc,yacc,zacc,heartrate,spo2,stepcount,temp,falldetection
```

The live prediction pipeline uses the same core sensor values sent by ESP32:

- `xAccel`, `yAccel`, `zAccel`
- acceleration magnitude and deviation from gravity
- `heartRate`, `spo2`, `temperature`

Outputs:

- `fallDetected`
- `fallProbability`
- `aiRiskLevel`: `NORMAL`, `CAUTION`, or `EMERGENCY`

The bundled CSV contains 300+ labelled demo rows so the project runs out of the box. Replace it with a real-world labelled fall dataset for stronger production accuracy.

## Gemini Chatbot Setup

Set your API key before starting Spring Boot. For local development, create `healthmonitor/.env`:

```text
MONGODB_URI=mongodb+srv://username:password@cluster.mongodb.net/?retryWrites=true&w=majority&appName=HealthWatch
MONGODB_DATABASE=HealthWatch
GEMINI_API_KEY=your_api_key_here
```

You can also set it in the terminal before running the backend:

```bat
set GEMINI_API_KEY=your_api_key_here
mvnw.cmd spring-boot:run
```

Optional model override:

```bat
set GEMINI_MODEL=gemini-2.5-flash
```

The default model is `gemini-2.5-flash`, configured in `application.properties`.

## Running

Start MongoDB locally, then from this folder run:

```bat
mvnw.cmd spring-boot:run
```

Open:

```text
../frontend/index.html
```

The frontend expects the backend at:

```text
http://localhost:8081
```

## Running With Docker

From the project root (`healthmonitorcopy`), build and start MongoDB, the backend, and the frontend:

```bat
docker compose up --build
```

Open the app at:

```text
http://localhost:3001
```

The backend health check endpoint is available at:

```text
http://localhost:8081/api/ping
```

MongoDB data is stored in the `mongo-data` Docker volume. To pass a Gemini key into the backend, set `GEMINI_API_KEY` in your shell before running Docker Compose.

## Deploying

### Railway Backend

Deploy the `healthmonitor` folder as the Railway service root. The project includes `railway.toml`, which starts the packaged Spring Boot jar and checks `/api/ping`.

Set these Railway variables:

```text
MONGODB_URI=mongodb+srv://username:password@cluster.mongodb.net/?retryWrites=true&w=majority&appName=HealthWatch
MONGODB_DATABASE=HealthWatch
GEMINI_API_KEY=your_gemini_key_optional
```

Use your MongoDB Atlas connection string for `MONGODB_URI`. `MONGODB_DATABASE` fixes the `Database name must not be empty` startup error when the Atlas URI does not include `/HealthWatch`. In Atlas, make sure Network Access allows your deploy host to connect. For simple project deployment, add `0.0.0.0/0` to Atlas Network Access.

Railway provides `PORT` automatically, and `application.properties` reads it with `server.port=${PORT:8081}`.

After deployment, open:

```text
https://your-railway-domain/api/ping
```

Expected response:

```json
{"service":"healthwatch-backend","status":"ok"}
```

### Vercel Frontend

Deploy the `frontend` folder as the Vercel project root. It is a static HTML/CSS/JS site and includes `vercel.json`.

After Railway gives you a public backend URL, put it in `frontend/js/config.js`:

```js
const BACKEND_API_BASE_URL = 'https://your-railway-domain';
```

Do not add a trailing slash. Redeploy the frontend after changing this value.
