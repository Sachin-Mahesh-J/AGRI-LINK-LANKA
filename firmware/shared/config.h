#pragma once

// Edit these values before flashing (copy from Admin Dashboard → IoT → Register device).

#define WIFI_SSID "SLT-4G_BBEE7"
#define WIFI_PASSWORD "Gimhan25115"

// Must match Firestore iotDevices.deviceId / ingestKey exactly
#define DEVICE_ID "ESP32-FARM-001"
#define INGEST_KEY "demo-key-esp32-farm-001"

// No trailing slash
#define FUNCTIONS_BASE_URL "https://us-central1-agriscout-4586c.cloudfunctions.net"

#define FIRMWARE_VERSION "2.0.0"

// Sensor node intervals (20s keeps phone/dashboard from marking readings stale)
#define SENSOR_READ_INTERVAL_MS 20000UL
#define SENSOR_RETRY_INTERVAL_MS 8000UL
#define HEARTBEAT_INTERVAL_MS 120000UL

// Camera node: scheduled cloud upload interval (1 minute for live monitoring)
#define CAPTURE_INTERVAL_MS 60000UL
#define CAPTURE_RETRY_INTERVAL_MS 15000UL
#define CAPTURE_FIRST_UPLOAD_DELAY_MS 20000UL

// Live MJPEG stream — stable QQVGA (matches Smart School camera foundation)
#define STREAM_FRAME_SIZE FRAMESIZE_QQVGA  // 160x120 — low lag / freeze-resistant
#define STREAM_JPEG_QUALITY 16             // 10 = best quality, 63 = smallest/fastest
#define STREAM_FRAME_INTERVAL_MS 0UL       // no artificial frame delay; GRAB_LATEST
