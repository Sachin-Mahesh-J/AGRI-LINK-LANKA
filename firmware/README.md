# AgriScout ESP32 Firmware

```
firmware/
├── shared/
│   ├── config.h          ← Wi-Fi, device ID, ingest key, Firebase URL
│   ├── wifi_manager.h
│   └── https_client.h
├── sensor-node/
│   ├── sensor-node.ino   ← Normal ESP32 + soil/DHT22/DS18B20/LCD
│   └── platformio.ini
└── camera-node/
    ├── camera-node.ino   ← ESP32-CAM
    └── platformio.ini
```

## Before flashing

1. **Admin Dashboard** → IoT → Register device
   - Sensor: device type **sensor**, copy **Device ID** + **Ingest key**
   - Camera: device type **camera**, same fields
2. Edit `shared/config.h` with your Wi-Fi and credentials
3. Deploy Cloud Functions (`ingestSensorReading` required; `ingestCameraImage` for camera)

## Arduino IDE — Sensor node (ESP32)

1. Install **ESP32** board package (Espressif)
2. Install libraries: DHT, OneWire, DallasTemperature, **hd44780**, ArduinoJson
3. Open `sensor-node/sensor-node.ino`
4. Board: **ESP32 Dev Module**, Port: your COM port
5. Upload

## Arduino IDE — Camera node (ESP32-CAM)

1. Board: **AI Thinker ESP32-CAM**
2. Open `camera-node/camera-node.ino`
3. GPIO0 → GND for flash mode; use FTDI adapter if needed
4. Upload
5. Endpoints (port 80):
   - Live stream: `http://<camera-ip>/stream`
   - Capture: `http://<camera-ip>/capture`
   - Status: `http://<camera-ip>/status` (includes `deviceId`)

Camera node is images/stream only. Soil/DHT/DS18B20 stay on the sensor node.

## PlatformIO

```bash
cd sensor-node
pio run -t upload

cd ../camera-node
pio run -t upload
```

## Wiring (sensor node)

| Sensor | ESP32 |
|--------|-------|
| Soil moisture (AOUT) | GPIO34 |
| DHT22 DATA | GPIO4 |
| DS18B20 DATA | GPIO5 |
| LCD I2C SDA/SCL | GPIO21 / GPIO22 |

## Test without hardware

Use curl against `ingestSensorReading` (see project README).
