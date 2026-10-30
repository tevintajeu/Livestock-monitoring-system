#!/usr/bin/env python3
"""
Mock ESP32 Edge Gateway HTTP Server
Simulates the localized ESP32 SoftAP Web Server (http://192.168.4.1/data or http://localhost:8080/data)
Serves 30 days of livestock telemetry CSV data for testing the Android Veterinary Client Application.

Unified CSV Schema:
node_id, temp, acc_x, acc_y, acc_z, distance, anomaly_flag, timestamp
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import random
import time

PORT = 8080

SAMPLE_NODES = [
    {"id": "NODE_001", "base_temp": 38.4, "type": "healthy"},           # Healthy cow (resting/grazing)
    {"id": "NODE_002", "base_temp": 39.8, "type": "fever"},             # Febrile state / Bovine Respiratory Disease
    {"id": "NODE_003", "base_temp": 38.5, "type": "healthy"},           # Healthy cow
    {"id": "NODE_004", "base_temp": 38.6, "type": "predator_motion"},   # High dynamic acceleration / Predator distress
    {"id": "NODE_005", "base_temp": 40.2, "type": "fever"},             # Critical fever / Acute infection
    {"id": "NODE_006", "base_temp": 38.2, "type": "healthy"},           # Healthy bull
    {"id": "NODE_007", "base_temp": 38.7, "type": "stray"},             # Geofence perimeter breach / Straying > 500m
]

def generate_csv_payload(rows_per_node=12):
    lines = ["node_id, temp, acc_x, acc_y, acc_z, distance, anomaly_flag, timestamp"]
    now_epoch = int(time.time())

    for node in SAMPLE_NODES:
        node_id = node["id"]
        node_type = node["type"]

        for i in range(rows_per_node):
            timestamp = now_epoch - (i * 3600)  # Each record 1 hour apart

            if node_type == "fever":
                temp = round(random.uniform(39.6, 41.1), 1)
                acc_x = round(random.uniform(-0.4, 0.4), 2)
                acc_y = round(random.uniform(-0.3, 0.3), 2)
                acc_z = round(random.uniform(9.6, 9.9), 2)
                distance = round(random.uniform(50.0, 320.0), 1)
                anomaly_flag = 1

            elif node_type == "predator_motion":
                temp = round(random.uniform(38.4, 39.2), 1)
                acc_x = round(random.uniform(3.5, 7.0), 2)
                acc_y = round(random.uniform(4.0, 8.5), 2)
                acc_z = round(random.uniform(12.0, 18.5), 2)
                distance = round(random.uniform(150.0, 480.0), 1)
                anomaly_flag = 1

            elif node_type == "stray":
                temp = round(random.uniform(38.2, 39.1), 1)
                acc_x = round(random.uniform(-0.5, 0.8), 2)
                acc_y = round(random.uniform(-0.4, 0.6), 2)
                acc_z = round(random.uniform(9.5, 9.9), 2)
                # Breaches the 500m geofence
                distance = round(random.uniform(540.0, 920.0), 1)
                anomaly_flag = 2

            else:  # healthy
                temp = round(random.uniform(38.0, 39.2), 1)
                acc_x = round(random.uniform(-0.3, 0.4), 2)
                acc_y = round(random.uniform(-0.2, 0.3), 2)
                acc_z = round(random.uniform(9.6, 9.9), 2)
                distance = round(random.uniform(30.0, 380.0), 1)
                anomaly_flag = 0

            lines.append(f"{node_id}, {temp}, {acc_x}, {acc_y}, {acc_z}, {distance}, {anomaly_flag}, {timestamp}")

    return "\n".join(lines)


class GatewayRequestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/data" or self.path == "/":
            payload = generate_csv_payload(rows_per_node=10)
            self.send_response(200)
            self.send_header("Content-Type", "text/csv; charset=utf-8")
            self.send_header("Content-Length", str(len(payload.encode("utf-8"))))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(payload.encode("utf-8"))
            print(f"[HTTP 200] Dispatched {len(payload.splitlines())} unified CSV records to client ({self.client_address[0]})")
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")

    def log_message(self, format, *args):
        # Clean logging
        print(f"[{self.log_date_time_string()}] {args[0] % args[1:]}")


def run():
    server_address = ("0.0.0.0", PORT)
    httpd = HTTPServer(server_address, GatewayRequestHandler)
    print("=" * 70)
    print(f"📡 Mock ESP32 Edge Gateway running on http://localhost:{PORT}/data")
    print(f"📱 For Android Emulator testing, use: http://10.0.2.2:{PORT}/data")
    print(f"📶 For Physical Phone testing on local Wi-Fi, use your PC's LAN IP")
    print("=" * 70)
    httpd.serve_forever()


if __name__ == "__main__":
    run()
