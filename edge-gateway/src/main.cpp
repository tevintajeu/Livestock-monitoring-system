#include <WiFi.h>
#include <PubSubClient.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <SPI.h>
#include <SD.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

#define LED_RED 32
#define LED_AMBER 33
#define ACK_BUTTON 27
#define SD_CS 15

const char* ssid = "Wokwi-GUEST";
const char* password = "";
const char* mqtt_server = "broker.hivemq.com";

WiFiClient espClient;
PubSubClient client(espClient);
SPIClass spiSD(HSPI);

void callback(char* topic, byte* payload, unsigned int length) {
  String incoming = "";
  for (int i = 0; i < length; i++) {
    incoming += (char)payload[i];
  }
  Serial.println("Received: " + incoming);

  // Parse CSV
int firstComma = incoming.indexOf(',');
int secondComma = incoming.indexOf(',', firstComma + 1);
int lastComma = incoming.lastIndexOf(',');

String nodeId = incoming.substring(0, firstComma);
// Extract everything between the first and second comma
float temp = incoming.substring(firstComma + 1, secondComma).toFloat(); 
int anomalyFlag = incoming.substring(lastComma + 1).toInt();
  
  // Log to Local SD Card
  File file = SD.open("/livestock.csv", FILE_APPEND);
  if (file) {
    file.println(incoming);
    file.close();
    Serial.println("Saved to SD File System");
  }

  // Update OLED Display
  display.clearDisplay();
  display.setCursor(0, 0);
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.println("FARM GATEWAY");
  display.println("ID: " + nodeId);
  display.println("Temp: " + String(temp) + " C");
  
  // Trigger Hardware Alerts
digitalWrite(LED_RED, LOW);
digitalWrite(LED_AMBER, LOW);
if (anomalyFlag == 1) {
  display.println("\nSTATUS: HEALTH/MOTION!");
  if (temp > 39.5) {
    digitalWrite(LED_AMBER, HIGH); // Sickness
  } else {
    digitalWrite(LED_RED, HIGH);   // Predator/Motion
  }
} else if (anomalyFlag == 2) {
  display.println("\nSTATUS: STRAY ALERT!");
  digitalWrite(LED_RED, HIGH);     // Straying
} else {
  display.println("\nSTATUS: Normal");
}
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
  Serial.println("Connecting to MQTT Broker...");
}

void setup() {
  Serial.begin(115200);
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_AMBER, OUTPUT);
  pinMode(ACK_BUTTON, INPUT_PULLUP);
  
  Wire.begin(21, 22);
  display.begin(SSD1306_SWITCHCAPVCC, 0x3C);
  display.clearDisplay();
  display.display();

  spiSD.begin(14, 12, 13, SD_CS);
  if (!SD.begin(SD_CS, spiSD)) {
    Serial.println("SD Card mount failed!");
  }

  setup_wifi();
  client.setServer(mqtt_server, 1883);
  client.setKeepAlive(90); 
  client.setCallback(callback);
}

void loop() {
  static int lastButtonReading = HIGH;
  static int buttonState = HIGH;
  static unsigned long lastDebounceTime = 0;

  if (!client.connected()) {
    while (!client.connected()) {
      if (client.connect("Gateway_TX_TNT99")) {
        client.subscribe("capstone/livestock/telemetry");
        break;
      }
      delay(100);
    }
  }
  client.loop();

  // Acknowledgment Button Logic
  int buttonReading = digitalRead(ACK_BUTTON);
  if (buttonReading != lastButtonReading) {
    lastDebounceTime = millis();
  }

  if (millis() - lastDebounceTime > 50 && buttonReading != buttonState) {
    buttonState = buttonReading;
    if (buttonState == LOW) {
      digitalWrite(LED_RED, LOW);
      digitalWrite(LED_AMBER, LOW);
      display.clearDisplay();
      display.setCursor(0, 0);
      display.println("Alert Acknowledged");
      display.display();

      File readFile = SD.open("/livestock.csv");
      if (readFile) {
        Serial.println("\n--- SD CARD LOG DUMP ---");
        while (readFile.available()) {
          Serial.write(readFile.read());
        }
        Serial.println("------------------------\n");
        readFile.close();
      } else {
        Serial.println("Error opening livestock.csv");
      }
    }
  }

  lastButtonReading = buttonReading;
}