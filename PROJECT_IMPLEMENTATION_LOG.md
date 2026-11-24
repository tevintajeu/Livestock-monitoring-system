# Strathmore University - Faculty of Information Technology
## ICS Project II: Off-Grid Livestock Monitoring System
### System Implementation & Hardware Bring-Up Engineering Log

---

## 1. Project Overview & System Topology

* **Course / Context:** 4.1 ICS Project II (Capstone Prototype)
* **Architecture Model:** Two-Node Off-Grid Edge-Gateway Model
  * **Node 1 (Wearable Collar Node - ESP32 #1):** Edge sensor telemetry node (Body temperature, 3-axis motion dynamics, geofence distance simulation, and local NVS flash backup).
  * **Node 2 (Edge Gateway - ESP32 #2):** Base station receiver (LoRa telemetry intake, SSD1306 OLED live display, local MicroSD CSV archive, visual triage LEDs, and hardware acknowledgment button).
  * **Client Tier:** Android Client Application (`vet-client-app`) for triage and veterinary records.

---

## 2. Hardware Architecture & Pin Mapping Reference

| Subsystem | Target Pin / Bus | Interface / Protocol | Component & Wiring Specs |
| :--- | :--- | :--- | :--- |
| **Collar MCU** | Microcontroller | USB / 3.3V Rail | ESP32 DevKit V1 (30-pin, CP2102 USB Bridge) |
| **Geofence Distance Simulator** | `GPIO 34` | Analog (ADC1) | 10 kΩ Potentiometer (Wiper to GPIO 34, Outer to 3V3 & GND) |
| **Bovine Temp Sensor** | `GPIO 4` | 1-Wire | DS18B20 Digital Probe (Data with 4.7 kΩ pull-up to 3V3) |
| **Activity / IMU Sensor** | `GPIO 21` (SDA), `GPIO 22` (SCL) | I2C | MPU6050 / MPU6500 (VCC to 3V3, GND to GND) |
| **Collar LoRa Transceiver** | `SCK=18`, `MISO=19`, `MOSI=23`, `NSS=5`, `RST=14`, `DIO0=26` | SPI | Ra-01 (SX1278 433 MHz, 3.3V strictly) |
| **Gateway MCU** | Base Station MCU | USB / 3.3V Rail | ESP32 DevKit V1 |
| **Gateway LoRa Receiver** | `SCK=18`, `MISO=19`, `MOSI=23`, `NSS=5`, `RST=14`, `DIO0=26` | SPI | Ra-01 (SX1278 433 MHz) |
| **Local OLED Readout** | `GPIO 21` (SDA), `GPIO 22` (SCL) | I2C (0x3C) | SSD1306 128x64 OLED |
| **Offline Logger** | `GPIO 15` (CS), `SCK=18`, `MISO=19`, `MOSI=23` | HSPI / SPI | MicroSD SPI Adapter module (`/livestock.csv`) |
| **Alert Indicators** | `GPIO 25` (Red), `GPIO 27` (Amber) | Digital Output | 5mm LEDs with 220 Ω current limiting resistors |
| **Alert Reset Button** | `GPIO 32` | Digital Input | Tactile Push Button to GND with internal `INPUT_PULLUP` |

---

## 3. Telemetry Payload Specification

All telemetry packets follow a structured 5-field CSV protocol:
```text
NODE_ID,TEMPERATURE_C,DYNAMIC_ACCEL_MS2,GEOFENCE_DISTANCE_M,ANOMALY_FLAG
```

### Anomaly Flag Definitions:
* **`0` (Normal):** Telemetry within nominal bovine ranges.
* **`1` (Health Alert):** Fever (> 39.5 °C) or Pre-Calving Drop (< 37.8 °C). Triggers 800 ms Amber LED pulse.
* **`2` (Geofence Stray Alert):** Simulated distance > 500 m. Triggers 400 ms Red LED blink.
* **`3` (Predator / Theft Spike):** Dynamic acceleration > 14.0 m/s². Triggers 150 ms alternating Red + Amber strobe.

---

## 4. Chronological Engineering & Bring-Up Log

### Entry 001 - Bench Hardware Transition & Initial Firmware Modernization
* **Date:** 2026-11-23
* **Milestone:** Transitioning from Wokwi browser simulation to physical hardware bring-up.
* **Actions Taken:**
  1. **Device Identification:** Connected physical ESP32 Node 1 to PC. Windows Device Manager detected `CP2102 USB to UART Bridge Controller` (`USB\VID_10C4&PID_EA60`).
  2. **Driver Diagnostic:** Identified Windows Error `Code 28` (`CM_PROB_FAILED_INSTALL`). Driver installation required before COM port enumeration.
  3. **Firmware Refactoring (`wearable-node/src/main.cpp`):**
     * **Wi-Fi Subsystem:** Migrated from `Wokwi-GUEST` to real 2.4 GHz credentials (`kiten-sdu n`). Added a 15-second non-blocking connection timeout to prevent device lockup if Wi-Fi is unreachable.
     * **I2C / IMU Resilience:** Removed blocking `while(1)` halt on MPU initialization. If MPU is disconnected during bench testing, firmware logs a warning and proceeds with 0.0 m/s² baseline rather than freezing the board.
     * **MQTT Session Stability:** Switched from a static `client_id` to dynamic MAC-based ID (`Node_TX_<chip_mac>`) to prevent connection dropping on `broker.hivemq.com`.
     * **Telemetry Streamer:** Configured 2.5-second continuous streaming to `capstone/livestock/telemetry` for live verification on the HiveMQ WebSocket client.
  4. **Compilation Verification:**
     * Built via PlatformIO (`platformio.ini` environment `esp32dev`).
     * Result: **100% SUCCESS**.
     * Memory Footprint: RAM: 13.9% (45,572 bytes / 327,680 bytes), Flash: 60.7% (794,989 bytes / 1,310,720 bytes).
     * Output binary generated at `wearable-node/.pio/build/esp32dev/firmware.bin`.

### Entry 002 - Driver Installation, Successful Flashing & Live HiveMQ Telemetry Verification
* **Date:** 2026-11-23
* **Milestones:**
  1. Driver enumeration: Silicon Labs CP210x driver successfully installed; board mapped to `COM7`.
  2. First physical firmware flash: PlatformIO uploaded `firmware.bin` to ESP32 (MAC: `d8:bf:c0:98:74:7c`) via `esptool.py` at 460800 baud.
  3. Network Debugging: Diagnosed Wi-Fi SSID case-sensitivity constraint (corrected `kiten-sdu n` to `Kiten-sdu n` on 2.4 GHz channel 10).
  4. Live End-to-End Streaming: ESP32 established connection with `broker.hivemq.com` and began continuous telemetry publishing to topic `capstone/livestock/telemetry`.
* **Hardware Validation Results:**
  * **DS18B20 Temperature Probe (GPIO 4):** Validated live. Reading ambient temperature ~24.8 °C - 25.1 °C with EMA filtering active.
  * **Geofence Potentiometer (GPIO 34 / ADC1):** Validated live. Dynamically sweeps between `0 m` and `1000 m`. Successfully tripped Anomaly Flag `2` (Stray Alert) when the wiper crossed the `> 500 m` threshold, and returned to Flag `0` (Normal) when below `500 m`.
  * **MQTT Streaming:** Verified live transmission packets:
    * `NODE_001,24.82,0.00,643,2 [ALERT TRIGGERED!]`
    * `NODE_001,24.81,0.00,31,0 [NORMAL]`
  * **Offline NVS Fallback:** Validated graceful fallback and packet archiving when link was temporarily offline during initial boot.

### Entry 003 - Hardware Self-Diagnostics, I2C Bus Scan & LoRa Radio Bring-Up
* **Date:** 2026-11-23
* **Milestones:**
  1. Automated I2C Diagnostic Scanner added to firmware boot routine (address range 0x08 - 0x77).
  2. Integrated Ra-01 LoRa 433 MHz SPI transceiver initialization (`CS=5, RST=14, DIO0=26`).
  3. Dynamic temperature increase validated live: temperature probe climbed from `24.81 °C` to `26.34 °C` under manual thermal stimulus.
  4. Geofence potentiometer actively tuned live from `550 m` to `640 m`, consistently raising Anomaly Flag `2`.
* **Hardware Diagnostic Findings:**
  * **I2C Bus (Pins 21/22):** Reported `No I2C devices responded`. MPU6050/6500 IMU is either not yet wired to the breadboard or has a loose connection on 3.3V/GND/SDA/SCL.
  * **LoRa Radio (Ra-01):** Reported `Ra-01 LoRa not detected on 433MHz`. SPI lines (18, 19, 23) or control pins (5, 14, 26) not yet attached or require verification.
  * **System Resilience:** Because non-blocking initialization was implemented, neither missing component halted the ESP32; it successfully maintained Wi-Fi, MQTT publishing, potentiometer ADC sampling, and temperature 1-Wire reading.

### Entry 004 - ADC Jitter Stabilization & Gateway SoftAP/WebServer Architecture
* **Date:** 2026-11-23
* **Milestones:**
  1. **Potentiometer ADC Filtering:** Implemented `readStableDistance()` on Node 1 with 32-sample burst oversampling and a ±15m deadband filter to eliminate RF power-supply jitter on GPIO 34.
  2. **Edge Gateway Companion Sync Architecture:**
     * Added `WebServer server(8080)` to `edge-gateway/src/main.cpp` with `/data` endpoint serving structured CSV (`node_id,temp_c,dyn_accel,distance_m,anomaly_flag`).
     * Broadcast local SoftAP `ESP32_LIVESTOCK_AP` (IP: `192.168.4.1`, Pass: `12345678`) enabling the Android mobile app (`vet-client-app`) to sync off-grid without requiring router infrastructure.
     * Implemented 2.5-second solid LED power-on self-test (POST) on boot to visually verify Red (GPIO 25) and Amber (GPIO 27) LED hardware polarity.
     * Converted Acknowledgment Button (GPIO 32) from level-triggered to falling-edge debouncing (`currentBtn == LOW && lastButtonState == HIGH`) to prevent floating ground suppression of alerts.

### Entry 005 - Dual-Node Concurrent Bring-Up, Live Cross-Node Telemetry & REST API Verification
* **Date:** 2026-11-24
* **Hardware Setup:**
  * **Wearable Collar Node (Node 1):** Enumerated on `COM7` (MAC: `d8:bf:c0:98:74:7c`).
  * **Edge Gateway (Node 2):** Enumerated on `COM8` (MAC: `48:af:f3:5a:b8:28`).
* **Validation Results:**
  1. **Dual-Node Simultaneous Flash:** Both `COM7` and `COM8` cleanly flashed via PlatformIO (`esp32dev` environment).
  2. **Gateway Boot Diagnostics (COM8):**
     * Alert LED Self-Test completed successfully (GPIO 25 & 27 solid ON for 2.5s).
     * SSD1306 OLED initialized on I2C address `0x3C`.
     * SoftAP broadcast active: `ESP32_LIVESTOCK_AP` (`192.168.4.1`).
     * Connected to local Wi-Fi `Kiten-sdu n` with Station IP `192.168.100.6`.
     * WebServer listening on port 8080 (`/data`).
  3. **Wearable Node Telemetry (COM7):**
     * Connected to Wi-Fi `Kiten-sdu n` with Station IP `192.168.100.5`.
     * Connected to `broker.hivemq.com` as `Node_TX_98c0bfd8`.
     * Publishing live telemetry at 2.5s intervals: `NODE_001,26.44,0.00,109,0`.
  4. **Cross-Node Telemetry Relay:**
     * Gateway on `COM8` received live packet from Node 1: `[GATEWAY RX] Packet: NODE_001,26.44,0.00,109,0`.
     * Rendered cow telemetry immediately to SSD1306 OLED display.
  5. **Android Companion App Endpoint Test:**
     * Sent HTTP GET request to `http://192.168.100.6:8080/data`.
     * Response: `HTTP 200 OK`
     * Payload:
       ```csv
       node_id,temp_c,dyn_accel,distance_m,anomaly_flag
       NODE_001,26.44,0.00,0,0
       ```
     * Confirmed 100% compatibility with Android `vet-client-app/network/Esp32SyncClient.java`.

---

## 5. Active Action Items & Next Steps

- [x] **Step 1:** Complete CP2102 driver installation on Windows host to assign COM ports (`COM7`, `COM8`).
- [x] **Step 2:** Flash `wearable-node` via PlatformIO to `COM7`.
- [x] **Step 3:** Flash `edge-gateway` via PlatformIO to `COM8`.
- [x] **Step 4:** Validate live telemetry transmission on HiveMQ WebSocket client (`broker.hivemq.com` -> `capstone/livestock/telemetry`).
- [x] **Step 5:** Bring up Node 2 (Edge Gateway) with dual Wi-Fi/MQTT, SSD1306 OLED, SoftAP (`192.168.4.1`), and WebServer (`:8080/data`).
- [x] **Step 6:** Validate cross-node telemetry reception on Edge Gateway.
- [ ] **Step 7:** Physical LoRa & MPU Module Jumper Wiring:
  - Wire Ra-01 LoRa 433 MHz SPI pins (SCK=18, MISO=19, MOSI=23, CS=5, RST=14, DIO0=26, VCC=3.3V, GND=GND) to enable RF transmission beyond Wi-Fi range.
  - Wire MPU6050/6500 IMU pins (SDA=21, SCL=22, VCC=3.3V, GND=GND) for physical 3-axis motion tracking.
- [ ] **Step 8:** Sync Android Companion App (`vet-client-app`) with Gateway SoftAP.
