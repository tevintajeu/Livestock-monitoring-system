#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <PubSubClient.h>
#include <SD.h>
#include <SPI.h>
#include <WiFi.h>
#include <Wire.h>


#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

#define LED_RED 32
#define LED_AMBER 33
#define ACK_BUTTON 27
#define SD_CS 15

const char *ssid = "Wokwi-GUEST";
const char *password = "";
const char *mqtt_server = "broker.hivemq.com";
const char *mqtt_topic = "capstone/livestock/telemetry";
const char *client_id = "Gateway_RX_TevinTajeu_1026";

WiFiClient espClient;
PubSubClient client(espClient);
SPIClass spiSD(HSPI);

// --- ALERT STATE MACHINE GLOBALS (Specific Objective 3) ---
int activeAlertPattern = 0; // 0 = Normal, 1 = Health (800ms), 2 = Stray
                            // (400ms), 3 = Predator/Theft (150ms)
bool alertAcknowledged =
    false; // Resets alert fatigue when farmer presses Pin 27
unsigned long lastBlinkTime = 0;
bool ledToggleState = false;
unsigned long lastBtnPress = 0;
unsigned long lastMqttRetry = 0;

// --- NON-BLOCKING PATTERN-ENCODED LED MATRIX ---
void updateAlertLeds() {
  if (activeAlertPattern == 0 || alertAcknowledged) {
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_AMBER, LOW);
    return;
  }

  unsigned long now = millis();

  if (activeAlertPattern == 1) {
    // Pattern 1: Health / Fever / Calving -> Steady 800ms pulse on Amber LED
    if (now - lastBlinkTime >= 800) {
      lastBlinkTime = now;
      ledToggleState = !ledToggleState;
      digitalWrite(LED_AMBER, ledToggleState ? HIGH : LOW);
      digitalWrite(LED_RED, LOW);
    }
  } else if (activeAlertPattern == 2) {
    // Pattern 2: Geofence Stray (>500m) -> Medium 400ms blink on Red LED
    if (now - lastBlinkTime >= 400) {
      lastBlinkTime = now;
      ledToggleState = !ledToggleState;
      digitalWrite(LED_RED, ledToggleState ? HIGH : LOW);
      digitalWrite(LED_AMBER, LOW);
    }
  } else if (activeAlertPattern == 3) {
    // Pattern 3: Predator / Theft Motion Spike -> Rapid 150ms alternating
    // strobe on Red & Amber LEDs
    if (now - lastBlinkTime >= 150) {
      lastBlinkTime = now;
      ledToggleState = !ledToggleState;
      digitalWrite(LED_RED, ledToggleState ? HIGH : LOW);
      digitalWrite(LED_AMBER, ledToggleState ? LOW : HIGH);
    }
  }
}

// --- MQTT TELEMETRY RECEIVER CALLBACK ---
void callback(char *topic, byte *payload, unsigned int length) {
  String incoming = "";
  for (int i = 0; i < length; i++) {
    incoming += (char)payload[i];
  }
  Serial.println("\n[GATEWAY RX] " + incoming);

  // Parse 5-Field CSV: NODE_ID,TEMP,DYNAMIC_ACCEL,DISTANCE,ANOMALY_FLAG
  int c1 = incoming.indexOf(',');
  int c2 = incoming.indexOf(',', c1 + 1);
  int c3 = incoming.indexOf(',', c2 + 1);
  int c4 = incoming.indexOf(',', c3 + 1);

  if (c1 == -1 || c2 == -1 || c3 == -1 || c4 == -1) {
    Serial.println("[ERROR] Malformed CSV frame ignored.");
    return;
  }

  String nodeId = incoming.substring(0, c1);
  float temp = incoming.substring(c1 + 1, c2).toFloat();
  float accel = incoming.substring(c2 + 1, c3).toFloat();
  int distance = incoming.substring(c3 + 1, c4).toInt();
  int anomalyFlag = incoming.substring(c4 + 1).toInt();

  // 1. Append to Local SD Card Storage (/livestock.csv)
  File file = SD.open("/livestock.csv", FILE_APPEND);
  if (file) {
    file.println(incoming);
    file.close();
    Serial.println("[SD STORAGE] Appended record to /livestock.csv");
  } else {
    Serial.println("[SD ERROR] Failed to write to /livestock.csv");
  }

  // 2. Determine Pattern-Encoded Alert Level & Unmute Alert State
  String statusLabel = "NORMAL";
  alertAcknowledged = false; // New incoming anomaly wakes up the visual alert

  if (anomalyFlag == 3 || accel > 14.0 || (distance > 500 && accel > 14.0)) {
    activeAlertPattern = 3; // Urgent 150ms Red+Amber Strobe
    statusLabel = "PREDATOR / THEFT!";
  } else if (anomalyFlag == 2 || distance > 500) {
    activeAlertPattern = 2; // 400ms Red Blink
    statusLabel = "STRAY ALERT (>500m)";
  } else if (anomalyFlag == 1 || temp > 39.5 || temp < 37.8) {
    activeAlertPattern = 1; // 800ms Amber Pulse
    statusLabel = (temp < 37.8) ? "CALVING DROP!" : "FEVER ALERT!";
  } else {
    activeAlertPattern = 0;
  }

  // 3. Render Farmer Triage Summary on SSD1306 OLED
  display.clearDisplay();
  display.setCursor(0, 0);
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.println("=== FARM GATEWAY ===");
  display.println("Cow ID: " + nodeId);
  display.println("Temp:   " + String(temp, 1) + " C");
  display.println("Motion: " + String(accel, 1) + " m/s2");
  display.println("Dist:   " + String(distance) + " m");
  display.println("--------------------");
  display.println(statusLabel);
  display.display();
}

void setup_wifi() {
  Serial.print("Connecting to WiFi");
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected!");
}

void reconnect_mqtt() {
  if (!client.connected() && (millis() - lastMqttRetry > 2000)) {
    lastMqttRetry = millis();
    Serial.print("Connecting to MQTT Broker...");
    if (client.connect(client_id)) {
      Serial.println(" Connected!");
      client.subscribe(mqtt_topic);
    } else {
      Serial.println(" Retry in 2s (rc=" + String(client.state()) + ")");
    }
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_AMBER, OUTPUT);
  pinMode(ACK_BUTTON, INPUT_PULLUP);

  Wire.begin(21, 22);
  if (display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    display.clearDisplay();
    display.setCursor(0, 0);
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.println("FARM GATEWAY READY");
    display.println("Waiting for alerts...");
    display.display();
  }

  // Initialize HSPI MicroSD Card & Create CSV File Header if Missing
  spiSD.begin(14, 12, 13, SD_CS);
  if (!SD.begin(SD_CS, spiSD)) {
    Serial.println("[SD ERROR] SD Card mount failed!");
  } else {
    if (!SD.exists("/livestock.csv")) {
      File initFile = SD.open("/livestock.csv", FILE_WRITE);
      if (initFile) {
        initFile.println("node_id,temp,acc_x,distance,anomaly_flag");
        initFile.close();
        Serial.println("[SD INIT] Created /livestock.csv with header");
      }
    }
  }

  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setKeepAlive(90);
  client.setCallback(callback);
}

void loop() {
  if (!client.connected()) {
    reconnect_mqtt();
  }
  client.loop();

  // Drive Non-Blocking Pattern-Encoded LEDs
  updateAlertLeds();

  // Non-Blocking Acknowledgment Button & SD Log Dump (Pin 27)
  if (digitalRead(ACK_BUTTON) == LOW && (millis() - lastBtnPress > 600)) {
    lastBtnPress = millis();
    alertAcknowledged = true;
    activeAlertPattern = 0;
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_AMBER, LOW);

    display.clearDisplay();
    display.setCursor(0, 0);
    display.println("=== FARM GATEWAY ===");
    display.println("\nAlert Acknowledged!");
    display.println("LEDs Silenced.");
    display.println("Dumping SD Log...");
    display.display();

    // Dump SD Card contents to Serial Monitor
    File readFile = SD.open("/livestock.csv");
    if (readFile) {
      Serial.println("\n--- SD CARD LOG DUMP (/livestock.csv) ---");
      while (readFile.available()) {
        Serial.write(readFile.read());
      }
      Serial.println("-----------------------------------------\n");
      readFile.close();
    } else {
      Serial.println("[SD ERROR] Could not open /livestock.csv for reading");
    }
  }
}