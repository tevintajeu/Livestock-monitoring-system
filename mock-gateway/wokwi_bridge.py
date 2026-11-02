#!/usr/bin/env python3
"""
Wokwi Simulation Bridge & ESP32 Edge Gateway Server
Listens to real-time MQTT events from your Wokwi simulation collar nodes on HiveMQ,
and serves them via HTTP GET /data to the Android MifugoSafe phone app.
"""

from mock_esp32_server import *

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
