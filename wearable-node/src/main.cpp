#include <WiFi.h>
#include <PubSubClient.h>
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_MPU6050.h>

#define ONE_WIRE_BUS 4
#define DISTANCE_PIN 34

OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);
Adafruit_MPU6050 mpu;

const char* ssid = "Wokwi-GUEST"; // Wokwi's default virtual WiFi
const char* password = "";
const char* mqtt_server = "broker.hivemq.com"; // Public MQTT broker

WiFiClient espClient;
PubSubClient client(espClient);

void setup_wifi() {
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
  }
}

void setup() {
  Serial.begin(115200);
  Wire.begin(21, 22);
  
  setup_wifi();
  client.setServer(mqtt_server, 1883);

  sensors.begin();
  if (!mpu.begin()) {
    Serial.println("MPU6050 Failed to initialize");
    while (1);
  }
}

void loop() {
  if (!client.connected()) {
    while (!client.connected()) {
      // 1. Fixed Client ID Collision
      if (client.connect("Node_TX_TNT99")) break; 
      delay(2000);
    }
  }
  client.loop();

  // Read Biometrics
  sensors.requestTemperatures();
  float temp = sensors.getTempCByIndex(0);
  int rawValue = analogRead(DISTANCE_PIN);
  int distance = map(rawValue, 0, 4095, 0, 1000);

  sensors_event_t a, g, temp_event;
  mpu.getEvent(&a, &g, &temp_event);

  // 2. Fixed Anomaly Logic (Includes Stray Alert)
  int anomalyFlag = 0;
  if (temp > 39.5 || abs(a.acceleration.x) > 15.0) {
    anomalyFlag = 1; // Health/Predator Alert
  } else if (distance > 500) {
    anomalyFlag = 2; // Stray Alert
  }

  // 3. Fixed CSV Payload (Includes distance instead of Y/Z)
  String payload = "NODE_001," + String(temp) + "," + String(a.acceleration.x) + "," + 
                   String(distance) + "," + String(anomalyFlag);

  // 4. Fixed Event-Driven Transmission (Only transmits on alert)
  if (anomalyFlag > 0) {
    client.publish("capstone/livestock/telemetry", payload.c_str());
    Serial.println("ALERT Transmitted: " + payload);
  } else {
    Serial.println("Normal state. Sleeping to save battery... (Distance: " + String(distance) + "m)");
  }

  // 5. Fixed Blocking Timer Delay
  unsigned long start = millis();
  while(millis() - start < 10) {
    client.loop();
    delay(10); 
  }
}