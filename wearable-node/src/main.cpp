#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <DallasTemperature.h>
#include <LoRa.h>
#include <OneWire.h>
#include <Preferences.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <Wire.h>

// --- PIN DEFINITIONS (COLLAR NODE) ---
#define ONE_WIRE_BUS 4
#define DISTANCE_PIN 34
#define LED_ONBOARD 2

// LoRa SX1278 (Ra-01 433MHz) Pins
#define LORA_CS 5
#define LORA_RST 14
#define LORA_DIO0 26

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);
Adafruit_MPU6050 mpu;
Preferences flashStorage;

// --- Wi-Fi & MQTT Configuration ---
const char *ssid = "Kiten-sdu n";
const char *password = "ARunke@123";
const char *mqtt_server = "broker.hivemq.com";
const char *mqtt_topic = "capstone/livestock/telemetry";

WiFiClient espClient;
PubSubClient client(espClient);
String clientId;

// --- SENSOR & HARDWARE STATUS GLOBALS ---
bool mpuReady = false;
bool isMpuDirect = false;
uint8_t mpuDirectAddr = 0x68;
bool loraReady = false;
float smoothedTemp = 38.5; // Baseline bovine temp (°C)
const float EMA_ALPHA = 0.6;
bool isFirstReading = true;
unsigned long lastMqttRetry = 0;

// Scan I2C bus to detect connected devices (MPU6050/6500)
void scanI2CDevices() {
  Serial.println("[I2C SCAN] Scanning I2C bus (SDA=21, SCL=22)...");
  byte count = 0;
  for (byte i = 8; i < 120; i++) {
    Wire.beginTransmission(i);
    if (Wire.endTransmission() == 0) {
      Serial.print(" -> Found device at 0x");
      if (i < 16)
        Serial.print("0");
      Serial.println(i, HEX);
      count++;
    }
  }
  if (count == 0) {
    Serial.println(
        " -> No I2C devices responded. Check MPU VCC/GND/SDA/SCL wiring.");
  }
}

// 1. Stabilized 32x Burst Oversampling Distance Filter (eliminates ADC RF jitter)
int readStableDistance() {
  long sum = 0;
  for (int i = 0; i < 32; i++) {
    sum += analogRead(DISTANCE_PIN);
    delayMicroseconds(150);
  }
  int avg = sum / 32;
  int dist = map(avg, 0, 4095, 0, 1000);
  dist = constrain(dist, 0, 1000);

  static int lastStable = 0;
  // ±10m deadband filter: ignores small noise ripples
  if (abs(dist - lastStable) > 10 || lastStable == 0) {
    lastStable = dist;
  }
  return lastStable;
}

// 2. Temperature Filter (EMA) with Disconnect Guard
float readFilteredTemperature() {
  sensors.requestTemperatures();
  float rawTemp = sensors.getTempCByIndex(0);

  // Reject hardware disconnect (-127C) or power-on default (+85C)
  if (rawTemp <= -50.0 || rawTemp == 85.0 || rawTemp == DEVICE_DISCONNECTED_C) {
    return smoothedTemp;
  }

  if (isFirstReading) {
    smoothedTemp = rawTemp;
    isFirstReading = false;
  } else {
    smoothedTemp = (EMA_ALPHA * rawTemp) + ((1.0 - EMA_ALPHA) * smoothedTemp);
  }
  return smoothedTemp;
}

// 2. 3-Axis Gravity-Compensated Dynamic Acceleration
float calculateDynamicAcceleration(float ax, float ay, float az) {
  float totalMagnitude = sqrt((ax * ax) + (ay * ay) + (az * az));
  return fabs(totalMagnitude - 9.81);
}

// 3. Flash Memory Fallback Archiver
void saveToFlashFallback(String payload) {
  flashStorage.begin("telemetry_bak", false);
  int count = flashStorage.getInt("count", 0);
  if (count < 20) {
    String key = "pkt_" + String(count);
    flashStorage.putString(key.c_str(), payload);
    flashStorage.putInt("count", count + 1);
    Serial.println("[FLASH FALLBACK] Offline: Saved packet #" +
                   String(count + 1) + " to NVS: " + payload);
  } else {
    Serial.println("[FLASH FALLBACK] NVS buffer full!");
  }
  flashStorage.end();
}

// 4. Auto-Stream Backlogged Packets on Reconnect
void flushFlashBacklog() {
  flashStorage.begin("telemetry_bak", false);
  int count = flashStorage.getInt("count", 0);
  if (count > 0) {
    Serial.println("[FLASH FALLBACK] Reconnected! Flushing " + String(count) +
                   " backlogged packets...");
    for (int i = 0; i < count; i++) {
      String key = "pkt_" + String(i);
      String savedPayload = flashStorage.getString(key.c_str(), "");
      if (savedPayload.length() > 0) {
        client.publish(mqtt_topic, savedPayload.c_str());
        Serial.println("[BACKLOG SENT] " + savedPayload);
        delay(100);
      }
    }
    flashStorage.clear();
  }
  flashStorage.end();
}

// 5. Non-Blocking Wi-Fi Setup with Timeout
void setup_wifi() {
  Serial.println("\n[WIFI] Connecting to SSID: " + String(ssid));
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);

  unsigned long startAttempt = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 4000) {
    delay(500);
    Serial.print(".");
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WIFI] Connected! IP Address: " +
                   WiFi.localIP().toString());
  } else {
    Serial.println(
        "\n[WIFI WARN] Connection timed out. Will retry in background loop.");
  }
}

// 6. Non-Blocking MQTT Reconnect
void reconnect_mqtt() {
  if (WiFi.status() != WL_CONNECTED) {
    if (millis() - lastMqttRetry > 5000) {
      lastMqttRetry = millis();
      WiFi.reconnect();
    }
    return;
  }

  if (!client.connected() && (millis() - lastMqttRetry > 3000)) {
    lastMqttRetry = millis();
    Serial.print("[MQTT] Connecting to broker.hivemq.com (Client ID: " +
                 clientId + ")...");
    if (client.connect(clientId.c_str())) {
      Serial.println(" CONNECTED!");
      flushFlashBacklog();
    } else {
      Serial.println(" Failed (rc=" + String(client.state()) +
                     "). Fallback to NVS.");
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=========================================");
  Serial.println("   LIVESTOCK WEARABLE NODE INITIALIZING   ");
  Serial.println("=========================================");

  // Unique MQTT client ID to prevent broker collisions
  clientId = "Node_TX_" + String((uint32_t)ESP.getEfuseMac(), HEX);

  // Initialize I2C Bus on GPIO 21 (SDA) and GPIO 22 (SCL) with timeout
  Wire.begin(21, 22);
  Wire.setTimeOut(50);
  Serial.println("[INIT] I2C bus initialized on SDA=21, SCL=22");

  // Initialize Temperature Sensor (DS18B20 on GPIO 4)
  sensors.begin();
  Serial.println("[INIT] DS18B20 Temperature bus started on GPIO 4");

  // Initialize IMU via robust Direct Register Driver (supports MPU6050, MPU6500, MPU9250)
  Wire.setClock(50000);
  uint8_t testAddrs[] = {0x68, 0x69};
  for (int a = 0; a < 2; a++) {
    uint8_t addr = testAddrs[a];
    Wire.beginTransmission(addr);
    byte err = Wire.endTransmission(true);
    if (err == 0) {
      // Device acknowledged address, now read WHO_AM_I register (0x75)
      Wire.beginTransmission(addr);
      Wire.write(0x75);
      if (Wire.endTransmission(true) == 0 && Wire.requestFrom((uint8_t)addr, (uint8_t)1) == 1) {
        uint8_t who = Wire.read();
        Serial.printf("[INIT OK] Detected MPU chip at 0x%02X (WHO_AM_I=0x%02X)\n", addr, who);
        // Wake up chip (clear SLEEP bit in PWR_MGMT_1 reg 0x6B)
        Wire.beginTransmission(addr);
        Wire.write(0x6B);
        Wire.write(0x00);
        Wire.endTransmission(true);
        // Set range +/- 8g in ACCEL_CONFIG reg 0x1C
        Wire.beginTransmission(addr);
        Wire.write(0x1C);
        Wire.write(0x10);
        Wire.endTransmission(true);
        mpuReady = true;
        isMpuDirect = true;
        mpuDirectAddr = addr;
        Serial.println("[INIT OK] Universal MPU-9250/6500/6050 driver activated!");
        break;
      }
    }
  }
  if (!mpuReady) {
    Serial.println(
        "[INIT WARN] MPU not detected. Continuing with 0.0 m/s^2 baseline.");
  }

  // Potentiometer on GPIO 34
  pinMode(DISTANCE_PIN, INPUT);
  Serial.println("[INIT] Geofence Potentiometer on GPIO 34 (ADC1)");

  // Onboard Activity LED
  pinMode(LED_ONBOARD, OUTPUT);
  digitalWrite(LED_ONBOARD, HIGH);

  // Initialize 433 MHz LoRa Radio (Ra-01 / SX1278)
  pinMode(LORA_CS, OUTPUT);
  digitalWrite(LORA_CS, HIGH);
  SPI.begin(18, 19, 23);

  // Hardware Pulse on LoRa RST pin (GPIO 14) to wake up SX1278
  pinMode(LORA_RST, OUTPUT);
  digitalWrite(LORA_RST, LOW);
  delay(10);
  digitalWrite(LORA_RST, HIGH);
  delay(10);

  // Diagnostic SPI Read on RegVersion (0x42)
  SPI.beginTransaction(SPISettings(1000000, MSBFIRST, SPI_MODE0));
  digitalWrite(LORA_CS, LOW);
  SPI.transfer(0x42 & 0x7F);
  uint8_t loraVersion = SPI.transfer(0x00);
  digitalWrite(LORA_CS, HIGH);
  SPI.endTransaction();
  Serial.print("[LORA DIAG] Raw SX1278 RegVersion read: 0x");
  if (loraVersion < 16) Serial.print("0");
  Serial.print(loraVersion, HEX);
  if (loraVersion == 0x12) {
    Serial.println(" (SX1278 DETECTED!)");
  } else if (loraVersion == 0x00) {
    Serial.println(" (ERROR: MISO/SCK grounded or CS pin not toggling)");
  } else if (loraVersion == 0xFF) {
    Serial.println(" (ERROR: MISO floating, module unpowered, or wrong pins)");
  } else {
    Serial.println(" (UNEXPECTED CHIP VERSION)");
  }

  LoRa.setPins(LORA_CS, LORA_RST, LORA_DIO0);
  LoRa.setSPI(SPI);
  if (!LoRa.begin(433E6)) {
    Serial.println("[INIT WARN] Ra-01 LoRa not detected on 433MHz (Check CS=5, "
                   "RST=14, DIO0=26, 3.3V).");
    loraReady = false;
  } else {
    Serial.println("[INIT OK] Ra-01 LoRa 433 MHz initialized successfully!");
    LoRa.setTxPower(17);
    loraReady = true;
  }

  // Wi-Fi & MQTT
  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setKeepAlive(60);

  Serial.println("[READY] Wearable Collar Node initialized.\n");
}

void loop() {
  if (!client.connected()) {
    reconnect_mqtt();
  }
  client.loop();

  // 1. Read Sensors
  float temp = readFilteredTemperature();
  int distance = readStableDistance();

  float dynamicAccel = 0.0;
  if (mpuReady) {
    if (isMpuDirect) {
      Wire.beginTransmission(mpuDirectAddr);
      Wire.write(0x3B);
      if (Wire.endTransmission(true) == 0 && Wire.requestFrom((uint8_t)mpuDirectAddr, (uint8_t)6) == 6) {
        int16_t rx = (Wire.read() << 8) | Wire.read();
        int16_t ry = (Wire.read() << 8) | Wire.read();
        int16_t rz = (Wire.read() << 8) | Wire.read();
        float ax = (rx / 4096.0f) * 9.806f;
        float ay = (ry / 4096.0f) * 9.806f;
        float az = (rz / 4096.0f) * 9.806f;
        dynamicAccel = calculateDynamicAcceleration(ax, ay, az);
      }
    } else {
      sensors_event_t a, g, temp_event;
      mpu.getEvent(&a, &g, &temp_event);
      dynamicAccel = calculateDynamicAcceleration(
          a.acceleration.x, a.acceleration.y, a.acceleration.z);
    }
  }

  // 2. Evaluate Anomaly Logic
  // Flag 1 = Health (fever >39.5C or drop <37.8C), Flag 2 = Stray (>500m), Flag
  // 3 = Predator (>14.0 m/s^2)
  int anomalyFlag = 0;
  if (dynamicAccel > 14.0) {
    anomalyFlag = 3;
  } else if (distance > 500) {
    anomalyFlag = 2;
  } else if (temp > 39.5 || (temp < 37.8 && temp > 35.0)) {
    anomalyFlag = 1;
  }

  // 3. Construct 5-Field CSV Payload:
  // NODE_ID,TEMP,DYNAMIC_ACCEL,DISTANCE,ANOMALY_FLAG
  String payload = "NODE_001," + String(temp, 2) + "," +
                   String(dynamicAccel, 2) + "," + String(distance) + "," +
                   String(anomalyFlag);

  // 4A. Broadcast over 433 MHz LoRa (if module is attached)
  if (loraReady) {
    digitalWrite(LED_ONBOARD, HIGH);
    LoRa.beginPacket();
    LoRa.print(payload);
    LoRa.endPacket();
    digitalWrite(LED_ONBOARD, LOW);
    Serial.println("[LORA TX 433MHz] " + payload);
  }

  // 4B. Stream to HiveMQ Cloud MQTT Broker
  if (client.connected()) {
    client.publish(mqtt_topic, payload.c_str());
    Serial.println("[HIVEMQ TX] " + payload +
                   (anomalyFlag > 0 ? " [ALERT TRIGGERED!]" : " [NORMAL]"));
  } else {
    Serial.println("[OFFLINE] " + payload);
    if (anomalyFlag > 0) {
      saveToFlashFallback(payload);
    }
  }

  // 5. Non-Blocking 2.5-Second Sampling Delay
  unsigned long start = millis();
  while (millis() - start < 2500) {
    client.loop();
    delay(20);
  }
}