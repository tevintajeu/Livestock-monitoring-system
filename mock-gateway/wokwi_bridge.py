import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
import paho.mqtt.client as mqtt
import time


MQTT_BROKER = "broker.hivemq.com"
MQTT_PORT = 1883
MQTT_TOPIC = "capstone/livestock/telemetry"
HTTP_PORT = 8080

telemetry_buffer = []

def on_connect(client, userdata, flags, rc, *args):
    print(f"[*] Connected to HiveMQ (code {rc}). Subscribing to '{MQTT_TOPIC}'...")
    client.subscribe(MQTT_TOPIC)

def on_message(client, userdata, msg):
    payload = msg.payload.decode("utf-8").strip()
    print(f"[MQTT RX from Wokwi] {payload}")
    
    parts = [p.strip() for p in payload.split(",")]
    if len(parts) == 5:
        # Rename NODE_ to COW_ so Android's header check doesn't discard it
        node_id = parts[0].replace("NODE_", "COW_")
        temp = parts[1]
        acc_x = parts[2]
        dist = parts[3]
        flag = parts[4]
        current_epoch = int(time.time())
        
        # 7-field schema: id, temp, acc_x, acc_y, acc_z, flag, timestamp
        payload = f"{node_id},{temp},{acc_x},0.0,9.8,{flag},{current_epoch}"
        print(f"[Transformed for App] {payload}")
    
    telemetry_buffer.append(payload)

class GatewayHttpHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/data":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()

            if not telemetry_buffer:
                csv_output = "node_id,temp,acc_x,acc_y,acc_z,anomaly_flag,timestamp\n"
            else:
                csv_output = "\n".join(telemetry_buffer) + "\n"

            self.wfile.write(csv_output.encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        return

def start_mqtt():
    try:
        client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1)
    except AttributeError:
        client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(MQTT_BROKER, MQTT_PORT, 60)
    client.loop_forever()

if __name__ == "__main__":
    mqtt_thread = threading.Thread(target=start_mqtt, daemon=True)
    mqtt_thread.start()

    server = HTTPServer(("0.0.0.0", HTTP_PORT), GatewayHttpHandler)
    print(f"[*] Bridge active on port {HTTP_PORT}. Waiting for Wokwi MQTT events...")
    server.serve_forever()