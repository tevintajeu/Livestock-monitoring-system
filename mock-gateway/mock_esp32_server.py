#!/usr/bin/env python3
"""
Wokwi Simulation Bridge & ESP32 Edge Gateway Server
Listens to real-time MQTT events from your Wokwi simulation collar nodes on HiveMQ,
and serves them via HTTP GET /data to the Android MifugoSafe phone app.

NO FAKE COWS: Only streams the real node(s) actively transmitting from Wokwi!
"""

import threading
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
import paho.mqtt.client as mqtt

PORT = 8080
MQTT_BROKER = "broker.hivemq.com"
MQTT_PORT = 1883
MQTT_TOPIC = "capstone/livestock/telemetry"

# In-memory buffer holding ONLY real telemetry received from Wokwi during this run
telemetry_records = []
buffer_lock = threading.Lock()

def on_connect(client, userdata, flags, rc, *args):
    if rc == 0:
        print(f"[*] Connected to HiveMQ Broker ({MQTT_BROKER}).")
        print(f"[*] Subscribed to topic: '{MQTT_TOPIC}'")
        print(f"[*] Waiting for your Wokwi simulation to send sensor readings...\n")
        client.subscribe(MQTT_TOPIC)
    else:
        print(f"[!] HiveMQ connection failed with code {rc}")

def on_message(client, userdata, msg):
    try:
        raw_payload = msg.payload.decode("utf-8").strip()
        print(f"\n[📡 Wokwi MQTT Received] {raw_payload}")

        parts = [p.strip() for p in raw_payload.split(",")]
        # Supported format: NODE_ID, TEMP, DYNAMIC_ACCEL, DISTANCE, ANOMALY_FLAG
        if len(parts) >= 5:
            node_id = parts[0]
            temp = float(parts[1])
            accel = float(parts[2])
            distance = float(parts[3])
            flag = int(parts[4])
            epoch_now = int(time.time())

            # Format 8-field unified CSV row:
            # node_id, temp, acc_x, acc_y, acc_z, distance, anomaly_flag, timestamp
            formatted_row = f"{node_id},{temp:.2f},{accel:.2f},0.00,9.80,{distance:.1f},{flag},{epoch_now}"

            with buffer_lock:
                # Keep last 50 readings of real active data so buffer doesn't grow indefinitely
                if len(telemetry_records) >= 50:
                    telemetry_records.pop(0)
                telemetry_records.append(formatted_row)

            print(f"    -> Parsed for App: {node_id} | {temp:.1f}°C | Motion: {accel:.1f} m/s² | Dist: {distance:.0f}m | Flag: {flag}")
            print(f"    -> Buffer now has {len(telemetry_records)} reading(s) for your active cow.")
    except Exception as e:
        print(f"[!] Error processing MQTT message: {e}")

class GatewayRequestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/data" or self.path == "/":
            with buffer_lock:
                if not telemetry_records:
                    # If Wokwi hasn't transmitted yet, return empty CSV with header
                    csv_output = "node_id,temp,acc_x,acc_y,acc_z,distance,anomaly_flag,timestamp\n"
                    record_count = 0
                else:
                    csv_output = "node_id,temp,acc_x,acc_y,acc_z,distance,anomaly_flag,timestamp\n" + "\n".join(telemetry_records) + "\n"
                    record_count = len(telemetry_records)

            self.send_response(200)
            self.send_header("Content-Type", "text/csv; charset=utf-8")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(csv_output.encode("utf-8"))

            print(f"\n[📱 Phone Sync] Dispatched {record_count} real record(s) to phone at {self.client_address[0]}")
            if record_count == 0:
                print("   (Note: Wokwi has not published any readings yet. Start your Wokwi simulation!)")

        elif self.path == "/clear":
            # Optional endpoint to reset buffer: http://localhost:8080/clear
            with buffer_lock:
                telemetry_records.clear()
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"Buffer Cleared OK\n")
            print("\n[🧹 Cleared] Telemetry buffer reset to 0 records.")
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")

    def log_message(self, format, *args):
        # Suppress noisy default HTTP log lines
        return

def start_mqtt_client():
    try:
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1)
    except AttributeError:
        client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = on_message
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.loop_forever()
    except Exception as e:
        print(f"[!] Could not connect to HiveMQ: {e}")

if __name__ == "__main__":
    print("=" * 70)
    print("🚀 MifugoSafe Real Wokwi Bridge Server")
    print(f"📡 Subscribing to: {MQTT_BROKER} -> '{MQTT_TOPIC}'")
    print(f"🌐 Serving HTTP endpoint: http://0.0.0.0:{PORT}/data")
    print("❌ Fake/Imaginary cows removed. Only streams your REAL simulation!")
    print("=" * 70)

    mqtt_thread = threading.Thread(target=start_mqtt_client, daemon=True)
    mqtt_thread.start()

    httpd = HTTPServer(("0.0.0.0", PORT), GatewayRequestHandler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
