#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <DallasTemperature.h>
#include <OneWire.h>
#include <Preferences.h> // Built-in ESP32 NVS Flash storage for Objective 2
#include <PubSubClient.h>
#include <WiFi.h>
#include <Wire.h>

#define ONE_WIRE_BUS 4
#define DISTANCE_PIN 34

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);
Adafruit_MPU6050 mpu;
Preferences flashStorage; // Non-volatile flash fallback

const char *ssid = "Wokwi-GUEST";
const char *password = "";
const char *mqtt_server = "broker.hivemq.com";
const char *mqtt_topic = "capstone/livestock/telemetry";
const char *client_id = "Node_TX_TevinTajeu_1026";

WiFiClient espClient;
PubSubClient client(espClient);

// --- SENSOR CALIBRATION & SMOOTHING GLOBALS ---
float smoothedTemp = 38.5; // Normal bovine baseline (°C)
const float EMA_ALPHA =
    0.6; // Smoothing weight (reacts quickly in Wokwi while filtering spikes)
bool isFirstReading = true;

// 1. Exponential Moving Average (EMA) Temperature Filter (Proposal Section 1.7)
float readFilteredTemperature() {
  sensors.requestTemperatures();
  float rawTemp = sensors.getTempCByIndex(0);

  // Reject hardware disconnect or CRC error values (-127C or +85C)
  if (rawTemp <= -50.0 || rawTemp == 85.0) {
    return smoothedTemp;
  }

  // Seed filter on boot so it immediately matches the Wokwi slider position
  if (isFirstReading) {
    smoothedTemp = rawTemp;
    isFirstReading = false;
  } else {
    smoothedTemp = (EMA_ALPHA * rawTemp) + ((1.0 - EMA_ALPHA) * smoothedTemp);
  }
  return smoothedTemp;
}

// 2. 3-Axis Gravity-Compensated Dynamic Acceleration (Proposal Section 1.6)
float calculateDynamicAcceleration(float ax, float ay, float az) {
  float totalMagnitude = sqrt((ax * ax) + (ay * ay) + (az * az));
  // Subtract Earth's 9.81 m/s^2 gravity so resting/tilting neck doesn't trigger
  // false alerts
  return fabs(totalMagnitude - 9.81);
}

// 3. Flash Memory Fallback Archiver (Proposal Specific Objective 2)
void saveToFlashFallback(String payload) {
  flashStorage.begin("telemetry_bak", false);
  int count = flashStorage.getInt("count", 0);
  if (count < 20) { // Store up to 20 backlogged packets in NVS Flash
    String key = "pkt_" + String(count);
    flashStorage.putString(key.c_str(), payload);
    flashStorage.putInt("count", count + 1);
    Serial.println("[FLASH FALLBACK] Link down! Saved packet #" +
                   String(count + 1) + " to NVS: " + payload);
  } else {
    Serial.println("[FLASH FALLBACK] NVS buffer full!");
  }
  flashStorage.end();
}

// 4. Auto-Stream Backlogged Frames on Reconnect (Proposal Specific Objective 2)
void flushFlashBacklog() {
  flashStorage.begin("telemetry_bak", false);
  int count = flashStorage.getInt("count", 0);
  if (count > 0) {
    Serial.println("[FLASH FALLBACK] Link restored! Streaming " +
                   String(count) + " backlogged packets...");
    for (int i = 0; i < count; i++) {
      String key = "pkt_" + String(i);
      String savedPayload = flashStorage.getString(key.c_str(), "");
      if (savedPayload.length() > 0) {
        client.publish(mqtt_topic, savedPayload.c_str());
        Serial.println("[BACKLOG SENT] " + savedPayload);
        delay(100);
      }
    }
    flashStorage.clear(); // Wipe backlog after successful transmission
  }
  flashStorage.end();
}

void setup_wifi() {
  Serial.print("Connecting to WiFi...");
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println(" Connected!");
}

void reconnect_mqtt() {
  // Non-blocking single reconnect attempt so the node never freezes if offline
  if (!client.connected()) {
    Serial.print("Attempting MQTT connection...");
    if (client.connect(client_id)) {
      Serial.println(" Connected to HiveMQ!");
      flushFlashBacklog(); // Stream any offline packets saved in flash
    } else {
      Serial.println(" Offline (rc=" + String(client.state()) +
                     "). Using local NVS Flash Fallback.");
    }
  }
}

void setup() {
  Serial.begin(115200);
  Wire.begin(21, 22);

  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setKeepAlive(60);

  sensors.begin();
  if (!mpu.begin()) {
    Serial.println("MPU6050 Failed to initialize");
    while (1)
      ;
  }
  Serial.println("Wearable Collar Node Initialized.");
}

void loop() {
  if (!client.connected()) {
    reconnect_mqtt();
  }
  client.loop();

  // 1. Read & Smooth Biometrics
  float temp = readFilteredTemperature();
  int rawValue = analogRead(DISTANCE_PIN);
  int distance = map(rawValue, 0, 4095, 0, 1000);

  sensors_event_t a, g, temp_event;
  mpu.getEvent(&a, &g, &temp_event);
  float dynamicAccel = calculateDynamicAcceleration(
      a.acceleration.x, a.acceleration.y, a.acceleration.z);

  // 2. Evaluate Anomaly Logic (Fever > 39.5C, Pre-Calving Drop < 37.8C,
  // Predator Motion > 14.0 m/s^2, or Stray > 500m)
  // 2. Evaluate Anomaly Logic (Distinct Flags 1, 2, and 3)
  int anomalyFlag = 0;
  if (dynamicAccel > 14.0) {
    anomalyFlag =
        3; // Flag 3: Predator / Theft Motion Spike (150ms Red+Amber Strobe)
  } else if (distance > 500) {
    anomalyFlag = 2; // Flag 2: Geofence Stray Alert (400ms Red Blink)
  } else if (temp > 39.5 || (temp < 37.8 && temp > 35.0)) {
    anomalyFlag =
        1; // Flag 1: Health Alert - Fever or Calving Drop (800ms Amber Pulse)
  }

  // 3. Construct 5-Field CSV Payload
  // (NODE_ID,TEMP,DYNAMIC_ACCEL,DISTANCE,ANOMALY_FLAG)
  String payload = "NODE_001," + String(temp, 2) + "," +
                   String(dynamicAccel, 2) + "," + String(distance) + "," +
                   String(anomalyFlag);

  // 4. Event-Driven Transmission with Offline Flash Fallback
  if (anomalyFlag > 0) {
    if (client.connected()) {
      client.publish(mqtt_topic, payload.c_str());
      Serial.println("[ALERT TRANSMITTED] " + payload);
    } else {
      saveToFlashFallback(payload);
    }
  } else {
    Serial.println("[NORMAL - SLEEP] Temp: " + String(temp, 1) +
                   "C | DynAccel: " + String(dynamicAccel, 1) +
                   "m/s2 | Dist: " + String(distance) + "m");
  }

  // 5. Non-Blocking 2.5-Second Sampling Timer (Fixes HiveMQ flooding)
  unsigned long start = millis();
  while (millis() - start < 2500) {
    client.loop();
    delay(20);
  }
}