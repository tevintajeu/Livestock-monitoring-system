#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <LoRa.h>
#include <PubSubClient.h>
#include <SD.h>
#include <SPI.h>
#include <WebServer.h>
#include <WiFi.h>
#include <Wire.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

// Hardware Pinout per Prototype Specification
#define LED_RED 25
#define LED_AMBER 27
#define LED_ONBOARD 2
#define ACK_BUTTON 32

// Dedicated Hardware SPI Bus for MicroSD (HSPI - completely independent of LoRa)
#define SD_CS 15
#define SD_SCK 16
#define SD_MOSI 17
#define SD_MISO 4
SPIClass sdSPI(HSPI);

// LoRa SX1278 (Ra-01 433MHz) Pins
#define LORA_CS 5
#define LORA_RST 14
#define LORA_DIO0 26

#if __has_include("secrets.h")
#include "secrets.h"
#else
#include "secrets.h.example"
#endif

const char *ssid = WIFI_SSID;
const char *password = WIFI_PASSWORD;
const char *mqtt_server = "broker.hivemq.com";
const char *mqtt_topic = "capstone/livestock/telemetry";

// SoftAP for Mobile Phone Direct Sync (Boma AP)
const char *ap_ssid = "ESP32_LIVESTOCK_AP";
const char *ap_pass = "12345678";

WiFiClient espClient;
PubSubClient client(espClient);
WebServer server(8080);

bool loraReady = false;
bool sdReady = false;
bool oledReady = false;
String lastReceivedPacket = "";
unsigned long lastPacketTime = 0;
bool isTimedOut = false;

// --- ALERT STATE MACHINE GLOBALS ---
int activeAlertPattern = 0; // 0 = Normal, 1 = Health (800ms), 2 = Stray (400ms), 3 = Predator (150ms)
bool alertAcknowledged = false;
unsigned long lastBlinkTime = 0;
bool ledToggleState = false;
unsigned long lastBtnPress = 0;
unsigned long lastMqttRetry = 0;
int lastButtonState = HIGH;
unsigned long lastHeartbeat = 0;

// Non-blocking Pattern-Encoded LED Driver
void updateAlertLeds() {
  unsigned long now = millis();

  if (activeAlertPattern == 0 || alertAcknowledged) {
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_AMBER, HIGH);   // Solid Amber = System Normal / Telemetry Active
    digitalWrite(LED_ONBOARD, HIGH); // Solid Onboard Blue = Gateway Active & Powered
    return;
  }

  if (activeAlertPattern == 1) {
    // Pattern 1: Health -> 800ms Amber pulse
    if (now - lastBlinkTime >= 800) {
      lastBlinkTime = now;
      ledToggleState = !ledToggleState;
      digitalWrite(LED_AMBER, ledToggleState ? HIGH : LOW);
      digitalWrite(LED_RED, LOW);
    }
  } else if (activeAlertPattern == 2) {
    // Pattern 2: Stray Geofence -> 400ms Red blink
    if (now - lastBlinkTime >= 400) {
      lastBlinkTime = now;
      ledToggleState = !ledToggleState;
      digitalWrite(LED_RED, ledToggleState ? HIGH : LOW);
      digitalWrite(LED_AMBER, LOW);
    }
  } else if (activeAlertPattern == 3) {
    // Pattern 3: Predator Spike -> 150ms alternating Red & Amber strobe
    if (now - lastBlinkTime >= 150) {
      lastBlinkTime = now;
      ledToggleState = !ledToggleState;
      digitalWrite(LED_RED, ledToggleState ? HIGH : LOW);
      digitalWrite(LED_AMBER, ledToggleState ? LOW : HIGH);
    }
  }
}

// Process Structured Telemetry Packet
void processTelemetryPacket(String incoming, int rssi = 0, float snr = 0.0) {
  lastReceivedPacket = incoming;
  lastPacketTime = millis();
  isTimedOut = false;
  Serial.println("\n[GATEWAY RX] Packet: " + incoming + (rssi != 0 ? " (RSSI: " + String(rssi) + " dBm, SNR: " + String(snr, 1) + " dB)" : ""));

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

  // 1. Append to SD Card (/telemetry.csv)
  if (sdReady) {
    File file = SD.open("/telemetry.csv", FILE_APPEND);
    if (file) {
      file.println(incoming);
      file.close();
      Serial.println("[SD STORAGE] Appended record to /telemetry.csv");
    } else {
      Serial.println("[SD ERROR] Failed to write to /telemetry.csv");
    }
  }

  // 2. Alert Logic & Reset Acknowledgment on new anomaly
  String statusLabel = "NORMAL";
  alertAcknowledged = false; // New incoming anomaly wakes up the visual alert

  if (anomalyFlag == 3 || accel > 14.0) {
    activeAlertPattern = 3;
    statusLabel = "PREDATOR / THEFT!";
  } else if (anomalyFlag == 2 || distance > 500) {
    activeAlertPattern = 2;
    statusLabel = "STRAY ALERT (>500m)";
  } else if (anomalyFlag == 1 || temp > 39.5) {
    activeAlertPattern = 1;
    statusLabel = "FEVER ALERT!";
  } else {
    activeAlertPattern = 0;
  }

  // 3. Render on SSD1306 OLED
  if (oledReady) {
    display.clearDisplay();
    display.setCursor(0, 0);
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.println("=== LIVESTOCK GATEWAY ===");
    display.println("Cow ID: " + nodeId);
    display.println("Temp:   " + String(temp, 1) + " C");
    display.println("Motion: " + String(accel, 1) + " m/s2");
    display.println("Dist:   " + String(distance) + " m");
    display.println("--------------------");
    display.println(statusLabel);
    display.display();
  }
}

// HTTP Server endpoint: returns CSV for Phone Vet Client App
void handleHttpData() {
  String responseData = "node_id,temp_c,dyn_accel,distance_m,anomaly_flag\n";
  if (lastReceivedPacket.length() > 0) {
    responseData += lastReceivedPacket + "\n";
  } else {
    responseData += "NO_SIGNAL,0.00,0.00,0,0\n";
  }
  server.send(200, "text/csv", responseData);
  Serial.println("[HTTP SYNC] Served telemetry CSV to connected phone.");
}

void handleHttpRoot() {
  server.send(200, "text/plain", "Livestock Edge Gateway Active. Connect to /data for CSV.");
}

// MQTT Callback (for hybrid Wi-Fi intake)
void mqttCallback(char *topic, byte *payload, unsigned int length) {
  String incoming = "";
  for (unsigned int i = 0; i < length; i++) {
    incoming += (char)payload[i];
  }
  processTelemetryPacket(incoming);
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=========================================");
  Serial.println("     EDGE GATEWAY INITIALIZING (NODE 2)  ");
  Serial.println("=========================================");

  pinMode(LED_RED, OUTPUT);
  pinMode(LED_AMBER, OUTPUT);
  pinMode(LED_ONBOARD, OUTPUT);
  pinMode(ACK_BUTTON, INPUT_PULLUP);

  // Turn ON Onboard LED (GPIO 2) immediately to prove ESP32 is powered and running
  digitalWrite(LED_ONBOARD, HIGH);

  // --- 3.5-SECOND HARDWARE LED TEST ON BOOT ---
  Serial.println("[LED TEST] Turning ON Red (GPIO 25) & Amber (GPIO 27) for 3.5 seconds...");
  digitalWrite(LED_RED, HIGH);
  digitalWrite(LED_AMBER, HIGH);
  delay(3500);
  digitalWrite(LED_RED, LOW);
  digitalWrite(LED_AMBER, HIGH); // Keep Amber ON to indicate Gateway Ready
  Serial.println("[LED TEST] Boot LED test finished (Amber set to ON).");

  // Check I2C Physical Pin States before attaching Wire
  uint8_t oledAddr = 0;
  pinMode(21, INPUT_PULLUP);
  pinMode(22, INPUT_PULLUP);
  delay(10);
  int sdaLevel = digitalRead(21);
  int sclLevel = digitalRead(22);
  Serial.printf("[I2C PIN CHECK] SDA (GPIO 21) = %s, SCL (GPIO 22) = %s\n", 
                sdaLevel ? "HIGH (OK)" : "LOW (STUCK/SHORTED TO GND!)", 
                sclLevel ? "HIGH (OK)" : "LOW (STUCK/SHORTED TO GND!)");

  if (sdaLevel == LOW || sclLevel == LOW) {
    Serial.println("[I2C FATAL] I2C bus line is held LOW! Cannot start I2C Wire.");
    Serial.println("  -> Disconnect OLED SDA & SCL wires to check for shorts.");
  } else {
    // Initialize I2C Bus on GPIO 21 (SDA) / GPIO 22 (SCL)
    Wire.begin(21, 22);
    Wire.setTimeOut(20);
    Serial.println("[I2C SCAN] Probing OLED at 0x3C and 0x3D...");
    Wire.beginTransmission(0x3C);
    if (Wire.endTransmission(true) == 0) {
      oledAddr = 0x3C;
    } else {
      Wire.beginTransmission(0x3D);
      if (Wire.endTransmission(true) == 0) {
        oledAddr = 0x3D;
      }
    }
  }

  if (oledAddr != 0) {
    Serial.printf("[INIT OK] SSD1306 OLED found at 0x%02X. Initializing...\n", oledAddr);
    if (display.begin(SSD1306_SWITCHCAPVCC, oledAddr)) {
      oledReady = true;
      display.clearDisplay();
      display.setTextColor(SSD1306_WHITE);
      display.setTextSize(1);
      display.setCursor(0, 0);
      display.println("=== LIVESTOCK GATEWAY ===");
      display.println("System Ready.");
      display.println("AP: ESP32_LIVESTOCK_AP");
      display.println("Waiting for packets...");
      display.display();
      Serial.println("[INIT OK] OLED display buffer written.");
    }
  } else {
    Serial.println("[INIT WARN] No SSD1306 OLED detected at 0x3C or 0x3D! Check VCC/GND/SDA/SCL wiring.");
  }

  // Initialize Dedicated HSPI Bus for MicroSD Card (Zero Interference with LoRa)
  pinMode(SD_CS, OUTPUT);
  digitalWrite(SD_CS, HIGH);
  pinMode(SD_MISO, INPUT_PULLUP);
  sdSPI.begin(SD_SCK, SD_MISO, SD_MOSI, SD_CS);

  // Initialize Dedicated VSPI Bus for LoRa SX1278
  pinMode(LORA_CS, OUTPUT);
  digitalWrite(LORA_CS, HIGH);
  SPI.begin(18, 19, 23); // LoRa VSPI: SCK=18, MISO=19, MOSI=23

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

  // Initialize LoRa 433 MHz Receiver
  LoRa.setPins(LORA_CS, LORA_RST, LORA_DIO0);
  LoRa.setSPI(SPI);
  delay(50);
  if (LoRa.begin(433E6) || (delay(100), LoRa.begin(433E6))) {
    loraReady = true;
    Serial.println("[INIT OK] Ra-01 LoRa Receiver started on 433 MHz.");
  } else {
    Serial.println("[INIT WARN] Ra-01 LoRa not detected. (Check CS=5, RST=14, DIO0=26, 3.3V).");
  }

  // Initialize MicroSD Card on Dedicated HSPI Bus with breadboard-safe 4 MHz clock (fallback to 1 MHz)
  if (SD.begin(SD_CS, sdSPI, 4000000) || SD.begin(SD_CS, sdSPI, 1000000)) {
    sdReady = true;
    Serial.println("[INIT OK] MicroSD Card mounted on dedicated HSPI (CS=15, SCK=16, MOSI=17, MISO=4).");
    if (!SD.exists("/telemetry.csv")) {
      File f = SD.open("/telemetry.csv", FILE_WRITE);
      if (f) {
        f.println("node_id,temp_c,dyn_accel,distance_m,anomaly_flag");
        f.close();
      }
    }
  } else {
    Serial.println("[INIT WARN] MicroSD Card failed to mount on dedicated HSPI.");
  }

  // Configure Wi-Fi in dual mode: SoftAP (for Phone) + STA (for Home/Bench Wi-Fi)
  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP(ap_ssid, ap_pass);
  Serial.println("[AP OK] SoftAP started: " + String(ap_ssid) + " (IP: " + WiFi.softAPIP().toString() + ")");

  WiFi.begin(ssid, password);
  unsigned long startWifi = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startWifi < 8000) {
    delay(400);
    Serial.print(".");
  }
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WIFI OK] Connected to " + String(ssid) + "! IP: " + WiFi.localIP().toString());
    client.setServer(mqtt_server, 1883);
    client.setCallback(mqttCallback);
    String gwClientId = "Gateway_RX_" + String((uint32_t)ESP.getEfuseMac(), HEX);
    if (client.connect(gwClientId.c_str())) {
      client.subscribe(mqtt_topic);
      Serial.println("[MQTT OK] Subscribed to " + String(mqtt_topic));
    }
  } else {
    Serial.println("\n[OFF-GRID] Running in standalone LoRa / SoftAP edge mode.");
  }

  // Start HTTP Server for Vet Client App on port 8080
  server.on("/data", handleHttpData);
  server.on("/", handleHttpRoot);
  server.begin();
  Serial.println("[HTTP OK] Web server running on port 8080. Phone endpoint: http://" + WiFi.softAPIP().toString() + ":8080/data");

  Serial.println("[READY] Edge Gateway initialized.\n");
}

void loop() {
  // Handle HTTP requests from Phone App
  server.handleClient();

  // Check for LoRa packets on 433 MHz
  if (loraReady) {
    int packetSize = LoRa.parsePacket();
    if (packetSize) {
      String loraPacket = "";
      while (LoRa.available()) {
        loraPacket += (char)LoRa.read();
      }
      processTelemetryPacket(loraPacket, LoRa.packetRssi(), LoRa.packetSnr());
    }
  }

  // Maintain MQTT if connected
  if (WiFi.status() == WL_CONNECTED) {
    if (client.connected()) {
      client.loop();
    }
  }

  // Update Visual LED Alert Pattern
  updateAlertLeds();

  // Telemetry Staleness Watchdog: If no packet arrives for > 7 seconds, clear stale data
  if (lastPacketTime > 0 && (millis() - lastPacketTime > 7000) && !isTimedOut) {
    isTimedOut = true;
    lastReceivedPacket = "";
    activeAlertPattern = 0;
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_AMBER, LOW);

    if (oledReady) {
      display.clearDisplay();
      display.setCursor(0, 0);
      display.setTextSize(1);
      display.setTextColor(SSD1306_WHITE);
      display.println("=== LIVESTOCK GATEWAY ===");
      display.println("Status: NO SIGNAL");
      display.println("Node 1: DISCONNECTED");
      display.println("Waiting for packets...");
      display.display();
    }
    Serial.println("[WATCHDOG] Node 1 silent >7s. Cleared stale screen and RAM data.");
  }

  // Handle Hardware Acknowledgment Button (GPIO 32) with Falling-Edge Debounce
  int currentBtn = digitalRead(ACK_BUTTON);
  if (currentBtn == LOW && lastButtonState == HIGH && (millis() - lastBtnPress > 250)) {
    lastBtnPress = millis();
    alertAcknowledged = true;
    activeAlertPattern = 0;
    digitalWrite(LED_RED, LOW);
    digitalWrite(LED_AMBER, LOW);

    if (oledReady) {
      display.clearDisplay();
      display.setCursor(0, 0);
      display.println("=== LIVESTOCK GATEWAY ===");
      display.println("\nAlert Acknowledged!");
      display.println("LEDs Silenced.");
      display.display();
    }
    Serial.println("[ACK] Alert fatigue acknowledged via Button GPIO 32. LEDs silenced.");
  }
  lastButtonState = currentBtn;

  // Serial Monitor Manual Injection for Independent Bench-Testing
  if (Serial.available()) {
    String serialPacket = Serial.readStringUntil('\n');
    serialPacket.trim();
    if (serialPacket.length() > 0) {
      Serial.println("[MANUAL INJECTION] Processing Serial Test Packet: " + serialPacket);
      processTelemetryPacket(serialPacket, -45, 9.5);
    }
  }
}
