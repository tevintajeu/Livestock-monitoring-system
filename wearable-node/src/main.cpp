/**
 * ============================================================================
 * LoRa-Based Low-Power Livestock Monitoring System - Wearable Collar Node
 * ============================================================================
 * 
 * Hardware:
 *   - Microcontroller: ESP32 or ESP8266 or Low-Power SAMD21/ATmega328P
 *   - Biometric Temp: DS18B20 / MAX30205 Clinical Body Temperature Sensor
 *   - Motion / IMU: MPU-6050 / ADXL345 3-Axis Accelerometer
 *   - Transceiver: SX1276 / SX1278 (868/915/433 MHz LoRa)
 * 
 * Transmitted Telemetry Payload Format:
 *   node_id, temp, acc_x, acc_y, acc_z, anomaly_flag
 *   Example: NODE_001, 39.5, 1.2, 0.5, 9.8, 1
 * 
 * Anomaly Logic:
 *   - temp > 39.5°C => Fever (anomaly_flag = 1)
 *   - dynamic acceleration spike > threshold => Distress / Predator (anomaly_flag = 1)
 */

#include <Arduino.h>

// Node Configuration
const char* NODE_ID = "NODE_001";
const float FEVER_THRESHOLD = 39.5;   // Livestock / Bovine febrile threshold in Celsius
const float ACC_DISTRESS_THRESHOLD = 14.0; // Dynamic acceleration magnitude threshold in m/s^2

// Mock reading generator for initial prototype calibration
float readTemperature() {
    // Normal baseline ~38.5°C; can be replaced with ds18b20.getTempC()
    return 38.6; 
}

void readAccelerometer(float &x, float &y, float &z) {
    // Resting posture with gravity along Z axis (9.81 m/s^2)
    x = 0.15;
    y = 0.08;
    z = 9.81;
}

void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n[INIT] Wearable Livestock Node Collar Initializing...");
}

void loop() {
    float temp = readTemperature();
    float ax, ay, az;
    readAccelerometer(ax, ay, az);

    // Compute 3D acceleration vector magnitude: |a| = sqrt(x^2 + y^2 + z^2)
    float accMagnitude = sqrt((ax * ax) + (ay * ay) + (az * az));

    // Determine Anomaly Flag
    int anomalyFlag = 0;
    if (temp > FEVER_THRESHOLD || accMagnitude > ACC_DISTRESS_THRESHOLD) {
        anomalyFlag = 1;
    }

    // Format packet: node_id, temp, acc_x, acc_y, acc_z, anomaly_flag
    char payload[96];
    snprintf(payload, sizeof(payload), "%s, %.2f, %.2f, %.2f, %.2f, %d",
             NODE_ID, temp, ax, ay, az, anomalyFlag);

    Serial.print("[LORA TX] Broadcasting Telemetry: ");
    Serial.println(payload);

    // Sleep for low power duty cycle (e.g., 60 seconds)
    delay(60000);
}
