#!/usr/bin/env python3
"""
Mock ESP32 Edge Gateway HTTP Server
Simulates the localized ESP32 SoftAP Web Server (http://192.168.4.1/data or http://localhost:8080/data)
Serves 30 days of livestock telemetry CSV data for testing the Android Veterinary Client Application.

CSV Schema:
node_id, temp, acc_x, acc_y, acc_z, anomaly_flag
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import random
import time

PORT = 8080

SAMPLE_NODES = [
    {"id": "NODE_001", "base_temp": 38.4, "anomaly_prob": 0.05},  # Healthy cow
    {"id": "NODE_002", "base_temp": 39.8, "anomaly_prob": 0.90},  # Febrile state (Fever)
    {"id": "NODE_003", "base_temp": 38.5, "anomaly_prob": 0.05},  # Healthy cow
    {"id": "NODE_004", "base_temp": 38.6, "anomaly_prob": 0.75},  # High movement / predator distress
    {"id": "NODE_005", "base_temp": 40.2, "anomaly_prob": 0.95},  # Critical infection / Acute fever
    {"id": "NODE_006", "base_temp": 38.2, "anomaly_prob": 0.02},  # Healthy bull
]

def generate_csv_payload(rows_per_node=15):
    lines = ["node_id, temp, acc_x, acc_y, acc_z, anomaly_flag"]
    now_epoch = int(time.time())

    for node in SAMPLE_NODES:
        node_id = node["id"]
        for i in range(rows_per_node):
            timestamp = now_epoch - (i * 3600)  # Each record 1 hour apart
            
            # Decide if anomaly
            is_anomaly = random.random() < node["anomaly_prob"]
            
            if is_anomaly:
                if "002" in node_id or "005" in node_id:
                    # Fever scenario
                    temp = round(random.uniform(39.6, 41.0), 2)
                    acc_x = round(random.uniform(-0.5, 0.5), 2)
                    acc_y = round(random.uniform(-0.5, 0.5), 2)
                    acc_z = round(random.uniform(9.6, 9.9), 2)
                    anomaly_flag = 1
                else:
                    # Motion / Distress scenario
                    temp = round(random.uniform(38.2, 39.1), 2)
                    acc_x = round(random.uniform(2.5, 6.0), 2)
                    acc_y = round(random.uniform(3.0, 7.5), 2)
                    acc_z = round(random.uniform(12.0, 18.0), 2)
                    anomaly_flag = 1
            else:
                # Normal resting/grazing vitals
                temp = round(random.uniform(38.0, 39.2), 2)
                acc_x = round(random.uniform(-0.3, 0.4), 2)
                acc_y = round(random.uniform(-0.2, 0.3), 2)
                acc_z = round(random.uniform(9.6, 9.9), 2)
                anomaly_flag = 0

            lines.append(f"{node_id}, {temp}, {acc_x}, {acc_y}, {acc_z}, {anomaly_flag}, {timestamp}")

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
            print(f"[HTTP 200] Dispatched {len(payload.splitlines())} CSV records to client ({self.client_address[0]})")
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
    print("=" * 65)
    print(f"📡 Mock ESP32 Edge Gateway running on http://localhost:{PORT}/data")
    print(f"📱 For Android Emulator testing, use: http://10.0.2.2:{PORT}/data")
    print(f"📶 For Physical Phone testing on local Wi-Fi, use your PC's LAN IP")
    print("=" * 65)
    httpd.serve_forever()


if __name__ == "__main__":
    run()
