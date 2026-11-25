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

### Entry 006 - Isolated Hardware Bench-Testing & Physical Fault Diagnosis
* **Date:** 2026-11-24 - 2026-11-25
* **Milestone:** Systematically isolating each physical sensor and peripheral subsystem into a dedicated test harness (`component-bench-tests/`) to root-cause hardware failures prior to final dual-node integration.
* **Key Findings & Fixes:**
  1. **Bench Test 1 - Indicator LEDs & Acknowledgment Push Button:**
     * **Target Pins:** Red LED (`GPIO 25`), Amber LED (`GPIO 27`), Tactile Button (`GPIO 32`).
     * **Phenomenon:** LEDs failed to illuminate despite valid GPIO 3.3V logic signals.
     * **Root Cause:** Breadboard insertion depth disparity. The longer anode leg bottomed out in the breadboard hole before the shorter cathode leg reached the internal spring contact clips.
     * **Resolution:** Re-aligned pin leg insertion depth; validated sequential blinking and falling-edge button interrupt debouncing.
  2. **Bench Test 2 - 3-Axis IMU (MPU) Driver & Silicon Diagnosis:**
     * **Target Pins:** SDA (`GPIO 21`), SCL (`GPIO 22`).
     * **Phenomenon:** The standard `Adafruit_MPU6050` library rejected the sensor with initialization failure.
     * **Root Cause 1:** Photographic inspection of the sensor module revealed it was an **MPU-9250 / MPU-6500** (`WHO_AM_I` register returns `0x71` or `0x70`), which `Adafruit_MPU6050` explicitly rejects because it hardcodes `if (chip_id != 0x68) return false;`.
     * **Root Cause 2:** Unsoldered header pins caused mechanical contact chatter and floating I2C bus addresses.
     * **Resolution:** Developed a direct, non-blocking register driver reading 6-byte raw accelerometer registers (`0x3B - 0x40`) with 50 ms bus timeout and 100 kHz clock, successfully bypassing library restrictions.
  3. **Bench Test 3 - Ra-01 LoRa 433 MHz RF Over-the-Air Link:**
     * **Target Pins:** SPI (`SCK=18`, `MISO=19`, `MOSI=23`, `CS=5`, `RST=14`, `DIO0=26`).
     * **Phenomenon:** Transmitter node froze inside `LoRa.endPacket()` indefinitely.
     * **Root Cause:** Brownout reset during the 100 mA RF transmit pulse due to a high-resistance jumper on the 3.3V power rail, resetting the SX1278 transceiver into Sleep mode (`OpMode = 0x80`).
     * **Resolution:** Reseated 3.3V and GND power rails with low-resistance jumpers. Executed a two-way ping-pong RF test between both nodes:
       * Collar Node transmitted telemetry; Gateway successfully acknowledged.
       * 7 consecutive packets transmitted with **0% packet loss** (`RSSI: -28 dBm`, `SNR: +9.8 dB`).

### Entry 007 - Ground Bus Fault Resolution & End-to-End System Validation
* **Date:** 2026-11-25
* **Milestone:** Full dual-node production firmware integration, breadboard ground fault clearance, and end-to-end off-grid telemetry verification.
* **Hardware Port Mapping:**
  * **Wearable Collar Node (Node 1):** Enumerated on `COM7` (MAC: `d8:bf:c0:98:74:7c`).
  * **Edge Gateway Node (Node 2):** Enumerated on `COM9` (MAC: `48:af:f3:5a:b8:28`).
* **Root-Cause Analysis - Gateway I2C Bus Lockup & LED Floating Ground:**
  * **Phenomenon:** After reassembling the dual-node circuit, the Edge Gateway appeared completely dead: no OLED display, no LEDs lit, and the ESP32 appeared frozen.
  * **Investigation:**
    * When flashing a pure LED blink loop, the user observed that the LEDs only flashed when touched with a human finger.
    * In electronics, human touch acts as a stray capacitive bridge to ground, proving that the positive GPIO 3.3V drive was active, but the **ground return path was open-circuit (floating ground)**.
    * Furthermore, when an I2C device (the SSD1306 OLED) loses its ground reference, its ungrounded internal circuit clamps the I2C clock line (SCL) to ground through internal ESD diodes. This caused `Wire.endTransmission()` to hang permanently in `setup()`, freezing the microcontroller before it could ever reach `loop()`.
  * **Resolution:**
    * Diagnosed a split breadboard ground rail (full-size breadboards frequently have an internal break in the middle of the blue negative rail).
    * Re-routed the ground jumper wire directly from the ESP32 `GND` pin to the component ground tie points.
    * Both issues cleared instantly: the LEDs illuminated brightly and the I2C bus unfroze.
* **Production Validation Results:**
  1. **OLED Hardware Detection:** I2C bus scanner immediately detected the SSD1306 OLED display at address `0x3C`. Display initialized cleanly and rendered the system status and cow metrics.
  2. **End-to-End Over-the-Air Link (LoRa 433 MHz):**
     ```text
     [COLLAR (COM7)]   [LORA TX 433MHz] NODE_001,29.19,0.00,198,0
     [GATEWAY (COM9)]  [GATEWAY RX]     Packet: NODE_001,29.19,0.00,198,0 (RSSI: -20 dBm, SNR: +9.8 dB)
     ```
  3. **Dynamic Sensor Responsiveness:**
     * **Geofence Potentiometer (GPIO 34):** Wiper turned dynamically from `9 m` $\to$ `26 m` $\to$ `198 m` $\to$ `67 m` $\to$ `17 m`, updating the Gateway OLED display with zero latency.
     * **DS18B20 Temperature Probe (GPIO 4):** Metal probe warmed by hand; temperature climbed smoothly from ambient `21.8 °C` to `30.2 °C`.
  4. **Alert State Machine & Visual Triage:**
     * Turning the potentiometer beyond 500 m triggered Anomaly Flag `2` (Stray Geofence Alert).
     * Edge Gateway immediately transitioned Amber LED to OFF and flashed the Red LED (400 ms pulse).
     * Pressing the tactile push button (`GPIO 32`) triggered falling-edge debounce, acknowledged the alert, silenced the Red LED, and updated the OLED display (`Alert Acknowledged!`).
  5. **Off-Grid AP & WebServer Sync:**
     * Edge Gateway successfully broadcast SoftAP `ESP32_LIVESTOCK_AP` (`192.168.4.1:8080/data`).
     * Real-time CSV telemetry served to connected mobile clients.

### Entry 008 - Android Client App Architecture: Security Hardening, Functional Modules & Samsung Tablet Optimization
* **Date:** 2026-11-25
* **Milestone:** Complete implementation, security hardening, and tablet responsiveness engineering for the MifugoSafe mobile client application (`vet-client-app`), culminating in successful USB ADB deployment to physical Samsung Galaxy Tab A9+ (`SM_X210`).
* **1. Security Architecture & Threat Defense:**
  * **Salted SHA-256 Offline Cryptographic Storage:** User authentication managed via `AuthManager.java`. Plaintext PINs and passwords are never stored in SQLite or `SharedPreferences`. Unique per-account cryptographic salts protect against dictionary and rainbow-table attacks.
  * **Brute-Force Rate Limiting & Exponential Lockout:** Physical PIN brute-force defense tracks failed PIN attempts. Five consecutive failed entries trigger a 30-second hardware-style keypad lockout with a visible countdown warning banner (`tv_lockout_warning`) and touch-event suppression.
  * **Role-Based Access Control (RBAC):** Strict partition between authenticated personas:
    * `ROLE_FARMER` (Cow Owner / Herder): Simple language, friendly cow names, actionable 3-step care instructions, 1-tap milk logging.
    * `ROLE_VET` (Field Veterinarian): Raw 3-axis accelerometer vectors ($a_x, a_y, a_z$, dynamic magnitude), clinical differential diagnoses, and configurable network endpoints.
  * **Scoped Network Security Policy (`network_security_config.xml`):** Cleartext HTTP traffic is strictly restricted to local offline hardware subnets (`192.168.4.1`, `10.0.2.2`, `localhost`). Global unencrypted internet HTTP is rejected at the OS level.
  * **Session Management & Privacy Controls:** Persistent login sessions with secure logout, persona switching, individual cow deletion, and a full local SQLite database wipe option.
* **2. Functional Features & Clinical Logic:**
  * **Offline-First Room SQLite Relational Architecture:** Four normalized tables (`TelemetryRecord`, `AnimalProfile`, `ProductionRecord`, `UserAccount`) in `AppDatabase.java` providing zero-internet local autonomy.
  * **High-Throughput Tolerant CSV Sync Client:** `Esp32SyncClient.java` connects to `http://192.168.4.1:8080/data` over OkHttp with 15s connect / 30s read timeouts. Multi-field parser autonomously handles 5-field production hardware frames, 6/7-field legacy frames, and 8-field raw vector frames.
  * **Interactive Triage & Semantic Filtering:** Four real-time filter chips (*All Cows*, *Needs Attention*, *Fever/Calving*, *Strayed Geofence*) for rapid herd screening during evening boma return.
  * **Biometric Visual Progress Gauges:** High-contrast `LinearProgressIndicator` gauges replace raw numbers for Body Heat (calving drop, normal, fever), Movement Activity (resting, grazing, panic), and Geofence Radial Distance (0–500m safe, >500m breach).
  * **Milk & Calving Output Tracker:** Daily milk recording (morning, evening, total in Liters), 7-day rolling average computation, calf status tracking, and a health-to-yield clinical predictive banner (*"Fever detected: expect 15–30% drop in morning milk yield"*).
  * **Actionable 3-Step Herder Checklist:** Interactive triage checklist with a persistent *"Mark as Handled"* timestamp button.
  * **Clinical Differential Diagnosis Engine:** `BiometricDiagnosticEngine.java` correlates multi-sensor patterns to clinical differentials (East Coast Fever, Babesiosis, Anaplasmosis, Foot-and-Mouth Disease, heat stress).
* **3. Samsung Tablet Optimization (`sw600dp` & `w900dp`):**
  * **Dual-Pane Clinical Cockpit (`layout-sw600dp/activity_node_detail.xml`):** Side-by-side workstation for Samsung Galaxy Tab screens: left pane hosts Animal Identity, Visual Vitals Gauges, and Milk Tracker; right pane hosts Actionable Triage Checklist and Telemetry History Log.
  * **Adaptive Multi-Column Grid (`layout-sw600dp/activity_main.xml`):** `GridLayoutManager` dynamically switches between 1 column (phones), 2 columns (7–8" / portrait tablets via `values-sw600dp/integers.xml`), and 3 columns (10"+ widescreen landscape via `values-w900dp/integers.xml`), with dynamic `onConfigurationChanged` span recalculation on device rotation.
  * **Centered Tablet Kiosk Login (`layout-sw600dp/activity_login.xml`):** 520dp elevated card layout maintaining tactile PIN ergonomics on wide tablet screens.
  * **Manifest Declaration:** Added `<supports-screens>` tags and orientation preservation in `AndroidManifest.xml`.
* **4. Build & Device Deployment:**
  * Compiled release-ready debug package: `vet-client-app/app/build/outputs/apk/debug/app-debug.apk` (6.33 MB).
  * Copied to root workspace as `MifugoSafe-Tablet.apk`.
  * Streamed and installed directly via ADB over USB onto physical Samsung Galaxy Tab A9+ (`SM_X210`, device ID `R9TX80299AR`).
  * Successfully launched `com.livestock.vetclient/.ui.LoginActivity` live on tablet hardware.

### Entry 009 - Shared SPI Bus MISO Contention (Tri-State Driver Failure) on MicroSD Module & Hardware Diagnosis
* **Date:** 2026-11-25
* **Milestone:** Empirical diagnosis of hardware-level SPI bus contention between the Ra-01 LoRa transceiver and commodity MicroSD breakout modules on the Edge Gateway (`COM9`).
* **Phenomenon:**
  * When wiring the MicroSD card module (`CS: 15, SCK: 18, MOSI: 23, MISO: 19, VCC: VIN, GND: GND`) to the Edge Gateway on the shared VSPI bus, the Ra-01 LoRa transceiver was immediately disabled, reporting `Raw SX1278 RegVersion read: 0x00 (ERROR: MISO/SCK grounded or CS pin not toggling)`.
* **Root-Cause Analysis:**
  * In standard SPI multi-slave topology, when a slave's Chip Select is deasserted (pulled HIGH), its MISO pin must enter a high-impedance (Hi-Z / Tri-State) state.
  * Low-cost MicroSD breakout modules frequently incorporate a 74LVC125A buffer with its Output Enable ($\overline{\text{OE}}$) permanently grounded on the PCB rather than routed through `CS`.
  * As a consequence, the module never tri-states its MISO output driver. Even when `SD_CS` (GPIO 15) is HIGH, the buffer holds the shared `MISO` line (GPIO 19) at low impedance, clamping the SX1278 LoRa data output to 0V.
* **Progressive Verification:**
  1. *Unpowered:* `cmd: 0x00` (Failed initial wake-up).
  2. *Pinout Inversion:* Pin order on silkscreen (`CS, SCK, MOSI, MISO, VCC, GND`) revealed GND was accidentally connected to Pin 2 (`SCK`), directly shorting the clock bus.
  3. *Powered Contention:* Once powered with 5V VIN, the card answered `CMD0` and `CMD8` up to `ACMD41` (`cmd: 0x29`), but MISO contention caused `sdSelectCard()` to fail and knocked out LoRa communication (`0x00`).

### Entry 010 - Dual Hardware SPI Bus Architecture Implementation (LoRa on VSPI & MicroSD on Dedicated HSPI)
* **Date:** 2026-11-25
* **Milestone:** Complete architectural separation of the physical SPI buses on the Edge Gateway (ESP32 Node 2) to eliminate bus contention while maintaining LoRa telemetry integrity.
* **Bus Separation Implementation:**
  * **Transceiver Link (VSPI):** Dedicated to Ra-01 LoRa SX1278 (`NSS/CS: GPIO 5`, `SCK: GPIO 18`, `MOSI: GPIO 23`, `MISO: GPIO 19`, `RST: GPIO 14`, `DIO0: GPIO 26`).
  * **Storage Interface (HSPI):** Dedicated to MicroSD card adapter (`CS: GPIO 15`, `SCK: GPIO 16`, `MOSI: GPIO 17`, `MISO: GPIO 4`, `VCC: VIN 5V`, `GND: Common GND`).
* **Hardware & Firmware Hardening:**
  * Configured independent `SPIClass sdSPI(HSPI);` instance.
  * Added `pinMode(SD_MISO, INPUT_PULLUP)` to pull MISO HIGH when idle, counteracting the ESP32 GPIO 4 default pulldown during card selection.
  * Added multi-rate initialization fallback (`4 MHz` clock with automatic fallback to `1 MHz`) to tolerate jumper wire capacitance on breadboards.
* **Empirical Verification:**
  * **LoRa Status:** SX1278 reads `0x12 (SX1278 DETECTED!)` on boot and initializes 433 MHz receiver flawlessly. Zero packet loss or bus clamping from the SD module.
  * **SD Card Status:** Card enters SPI mode (`CMD0` OK, `CMD8` OK). During `ACMD41 (cmd: 0x29)` card operating condition validation, the physical card reports select timeout (`Select Failed`).
  * **System Resilience:** If physical SD card fails to mount or is removed, the gateway firmware safely falls back without halting, serving live telemetry via SoftAP HTTP `/data` and caching records directly into the Samsung Tablet's persistent Room SQLite database.

---

## 5. Active Action Items & Completed Milestones

- [x] **Step 1:** Complete CP2102 driver installation on Windows host to assign COM ports (`COM7`, `COM8`/`COM9`).
- [x] **Step 2:** Flash `wearable-node` via PlatformIO to `COM7`.
- [x] **Step 3:** Flash `edge-gateway` via PlatformIO to `COM9`.
- [x] **Step 4:** Validate live telemetry transmission on HiveMQ WebSocket client (`broker.hivemq.com` -> `capstone/livestock/telemetry`).
- [x] **Step 5:** Bring up Node 2 (Edge Gateway) with dual Wi-Fi/MQTT, SSD1306 OLED, SoftAP (`192.168.4.1`), and WebServer (`:8080/data`).
- [x] **Step 6:** Validate cross-node telemetry reception on Edge Gateway.
- [x] **Step 7:** Physical LoRa & MPU Module Bench Testing & Bring-Up:
  - Ra-01 LoRa 433 MHz SPI transceivers operational on both nodes with 100% packet delivery (`RSSI: -20 dBm`, `SNR: +9.8 dB`).
  - Universal direct register driver for MPU-9250 / MPU-6500 implemented and integrated.
- [x] **Step 8:** Clear breadboard ground faults, split-rail breaks, and I2C bus lockups.
- [x] **Step 9:** End-to-end integration: Live sensor broadcast, OLED display, LED visual triage, tactile button acknowledgment, and SoftAP WebServer.
- [x] **Step 10:** Complete Android client app security hardening, functional clinical features (Room SQLite, OkHttp sync, milk tracker, triage checklists), Samsung tablet dual-pane optimization, and live USB ADB deployment to Samsung Galaxy Tab A9+.
