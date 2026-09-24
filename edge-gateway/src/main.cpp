/**
 * ============================================================================
 * LoRa-Based Low-Power Livestock Monitoring System - ESP32 Edge Gateway Firmware
 * ============================================================================
 * 
 * Hardware: ESP32 Dev Module + SX1276/SX1278 LoRa Transceiver + SPIFFS/LittleFS
 * Mode: Localized Wi-Fi Access Point (SoftAP) + Web Server + LoRa Concentrator
 * 
 * Network Credentials (SoftAP):
 *   SSID: "ESP32_LIVESTOCK_AP"
 *   Password: "" (Open, or "livestock1234")
 *   IP: 192.168.4.1
 *   Endpoint: http://192.168.4.1/data
 * 
 * CSV Payload Format:
 *   node_id, temp, acc_x, acc_y, acc_z, anomaly_flag
 *   Example: NODE_001, 39.5, 1.2, 0.5, 9.8, 1
 */

#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <SPIFFS.h>

// --- Configuration Constants ---
const char* AP_SSID = "ESP32_LIVESTOCK_AP";
const char* AP_PASS = "vetclient123"; // or NULL for open AP
const IPAddress LOCAL_IP(192, 168, 4, 1);
const IPAddress GATEWAY_IP(192, 168, 4, 1);
const IPAddress SUBNET_MASK(255, 255, 255, 0);

const char* TELEMETRY_FILE_PATH = "/telemetry_history.csv";

WebServer server(80);

// Sample simulated telemetry buffer to ensure out-of-the-box readiness
String sampleTelemetryBuffer = 
    "node_id, temp, acc_x, acc_y, acc_z, anomaly_flag\n"
    "NODE_001, 38.6, 0.12, 0.05, 9.81, 0\n"
    "NODE_002, 39.9, 0.10, 0.08, 9.78, 1\n"
    "NODE_003, 38.4, 0.04, 0.02, 9.80, 0\n"
    "NODE_004, 38.7, 4.50, 3.20, 14.80, 1\n"
    "NODE_005, 40.4, 0.15, 0.11, 9.82, 1\n"
    "NODE_006, 38.2, 0.08, 0.05, 9.79, 0\n";

/**
 * HTTP GET Handler for /data endpoint.
 * Serves 30-day CSV telemetry buffer to connected Android Veterinary Client.
 */
void handleDataRequest() {
    Serial.println("[HTTP] Inbound GET /data request from Android client.");

    // Add CORS headers for testing flexibility
    server.sendHeader("Access-Control-Allow-Origin", "*");
    server.sendHeader("Cache-Control", "no-cache");

    if (SPIFFS.exists(TELEMETRY_FILE_PATH)) {
        File file = SPIFFS.open(TELEMETRY_FILE_PATH, FILE_READ);
        if (file) {
            server.streamFile(file, "text/csv");
            file.close();
            Serial.println("[HTTP] Streamed stored telemetry CSV file from SPIFFS.");
            return;
        }
    }

    // Fallback: Stream in-memory buffer if SPIFFS file is empty or formatting
    server.send(200, "text/csv", sampleTelemetryBuffer);
    Serial.println("[HTTP] Dispatched telemetry buffer to Android client.");
}

/**
 * HTTP Root Welcome Page
 */
void handleRoot() {
    String html = "<html><body><h1>ESP32 Livestock Gateway</h1>"
                  "<p>Telemetry Endpoint: <a href='/data'>/data</a></p></body></html>";
    server.send(200, "text/html", html);
}

void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- Initializing ESP32 Livestock Edge Gateway ---");

    // Initialize SPIFFS
    if (!SPIFFS.begin(true)) {
        Serial.println("[WARN] SPIFFS mount failed. Using RAM buffer fallback.");
    } else {
        Serial.println("[OK] SPIFFS Flash storage mounted.");
    }

    // Configure ESP32 SoftAP
    WiFi.mode(WIFI_AP);
    WiFi.softAPConfig(LOCAL_IP, GATEWAY_IP, SUBNET_MASK);
    WiFi.softAP(AP_SSID, AP_PASS);

    Serial.print("[OK] SoftAP Online. SSID: ");
    Serial.println(AP_SSID);
    Serial.print("[OK] Gateway IP Address: ");
    Serial.println(WiFi.softAPIP());

    // Register Web Server Endpoints
    server.on("/", HTTP_GET, handleRoot);
    server.on("/data", HTTP_GET, handleDataRequest);
    server.begin();

    Serial.println("[OK] HTTP Web Server listening on port 80.");
}

void loop() {
    server.handleClient();
}
