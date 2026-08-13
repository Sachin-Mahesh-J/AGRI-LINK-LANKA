/**
 * AgriScout ESP32-CAM Camera Node v2
 *
 * Board: AI Thinker ESP32-CAM
 * Role: live MJPEG (LAN) + scheduled cloud JPEG uploads.
 *
 * Notes:
 *  - While /stream is open, cloud uploads pause (ESP32 can't do both well).
 *  - After stream ends, uploads resume automatically.
 *  - Idle loop drains frames to prevent cam_hal FB-OVF spam.
 */
#include "esp_camera.h"
#include <WiFi.h>
#include <string.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

#include "../shared/config.h"
#include "../shared/https_client.h"

#undef DEVICE_ID
#undef INGEST_KEY
#define DEVICE_ID "ESP32-CAM-001"
#define INGEST_KEY "demo-key-esp32-cam-001"

#define PWDN_GPIO_NUM 32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM 0
#define SIOD_GPIO_NUM 26
#define SIOC_GPIO_NUM 27
#define Y2_GPIO_NUM 5
#define Y3_GPIO_NUM 18
#define Y4_GPIO_NUM 19
#define Y5_GPIO_NUM 21
#define Y6_GPIO_NUM 36
#define Y7_GPIO_NUM 39
#define Y8_GPIO_NUM 34
#define Y9_GPIO_NUM 35
#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM 23
#define PCLK_GPIO_NUM 22
#define LED_FLASH_GPIO 4

WiFiServer server(80);
HttpsClient https;

bool cameraReady = false;
volatile bool streamActive = false;
unsigned long lastCaptureMs = 0;
unsigned long wifiConnectedAtMs = 0;
unsigned long cloudIntervalMs = CAPTURE_INTERVAL_MS;
bool initialUploadDone = false;
bool bootInfoPrinted = false;

void setFlash(bool on) {
  pinMode(LED_FLASH_GPIO, OUTPUT);
  digitalWrite(LED_FLASH_GPIO, on ? HIGH : LOW);
}

void applySensorDefaults() {
  sensor_t* s = esp_camera_sensor_get();
  if (!s) return;
  s->set_framesize(s, FRAMESIZE_QQVGA);
  s->set_quality(s, 16);
  s->set_brightness(s, 1);
  s->set_contrast(s, 1);
  s->set_saturation(s, 0);
  s->set_sharpness(s, 1);
  s->set_whitebal(s, 1);
  s->set_awb_gain(s, 1);
  s->set_exposure_ctrl(s, 1);
  s->set_aec2(s, 1);
  s->set_ae_level(s, 1);
  s->set_gain_ctrl(s, 1);
  s->set_gainceiling(s, GAINCEILING_16X);
  s->set_bpc(s, 1);
  s->set_wpc(s, 1);
  s->set_lenc(s, 1);
  s->set_hmirror(s, 0);
  s->set_vflip(s, 0);
}

void drainCameraFrames(int count = 1) {
  if (!cameraReady) return;
  for (int i = 0; i < count; i++) {
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) break;
    esp_camera_fb_return(fb);
  }
}

void connectWiFi() {
  Serial.printf("[WiFi] Connecting to %s", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 40) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    const IPAddress local = WiFi.localIP();
    const IPAddress gateway = WiFi.gatewayIP();
    const IPAddress subnet = WiFi.subnetMask();
    WiFi.config(local, gateway, subnet, IPAddress(8, 8, 8, 8), IPAddress(1, 1, 1, 1));
    wifiConnectedAtMs = millis();
    Serial.printf("\n[WiFi] Connected. IP: %s  DNS: %s  RSSI: %d dBm\n",
                  WiFi.localIP().toString().c_str(),
                  WiFi.dnsIP().toString().c_str(),
                  WiFi.RSSI());
  } else {
    Serial.println("\n[WiFi] Failed. Will retry in loop.");
  }
}

bool initCamera() {
  setFlash(false);

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_QQVGA;
  config.jpeg_quality = 16;
  config.fb_count = 1;  // 1 buffer = stable stream, far less FB-OVF
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  config.fb_location = psramFound() ? CAMERA_FB_IN_PSRAM : CAMERA_FB_IN_DRAM;

  Serial.printf("[CAM] Init QQVGA fb_count=1 psram=%s\n",
                psramFound() ? "yes" : "no");

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[CAM] Init failed: 0x%x\n", err);
    cameraReady = false;
    return false;
  }

  applySensorDefaults();
  drainCameraFrames(2);
  cameraReady = true;
  Serial.println("[CAM] Camera initialized OK");
  return true;
}

camera_fb_t* captureStillWithFlash() {
  if (!cameraReady) return nullptr;

  setFlash(true);
  delay(120);
  // Let AEC settle with flash on.
  drainCameraFrames(3);
  delay(60);
  camera_fb_t* fb = esp_camera_fb_get();
  setFlash(false);
  return fb;
}

void sendEmpty(WiFiClient& client, const char* statusLine) {
  client.print("HTTP/1.1 ");
  client.println(statusLine);
  client.println("Content-Length: 0");
  client.println("Connection: close");
  client.println();
}

void sendJson(WiFiClient& client, const String& body) {
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: application/json");
  client.println("Access-Control-Allow-Origin: *");
  client.println("Cache-Control: no-store");
  client.print("Content-Length: ");
  client.println(body.length());
  client.println("Connection: close");
  client.println();
  client.print(body);
}

void handleStatus(WiFiClient& client) {
  String body = "{";
  body += "\"deviceId\":\"" + String(DEVICE_ID) + "\",";
  body += "\"ip\":\"" + WiFi.localIP().toString() + "\",";
  body += "\"cameraReady\":" + String(cameraReady ? "true" : "false") + ",";
  body += "\"streamActive\":" + String(streamActive ? "true" : "false") + ",";
  body += "\"psram\":" + String(psramFound() ? "true" : "false") + ",";
  body += "\"wifiRSSI\":" + String(WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : 0);
  body += "}";
  sendJson(client, body);
}

String base64Encode(const uint8_t* data, size_t len) {
  static const char* table =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  String out;
  out.reserve(((len + 2) / 3) * 4);
  for (size_t i = 0; i < len; i += 3) {
    uint32_t a = i < len ? data[i] : 0;
    uint32_t b = i + 1 < len ? data[i + 1] : 0;
    uint32_t c = i + 2 < len ? data[i + 2] : 0;
    uint32_t triple = (a << 16) + (b << 8) + c;
    out += table[(triple >> 18) & 0x3F];
    out += table[(triple >> 12) & 0x3F];
    out += (i + 1 < len) ? table[(triple >> 6) & 0x3F] : '=';
    out += (i + 2 < len) ? table[triple & 0x3F] : '=';
  }
  return out;
}

bool uploadCapture(camera_fb_t* fb) {
  String imageBase64 = base64Encode(fb->buf, fb->len);
  String payload = "{";
  payload += "\"deviceId\":\"" + String(DEVICE_ID) + "\",";
  payload += "\"width\":" + String(fb->width) + ",";
  payload += "\"height\":" + String(fb->height) + ",";
  payload += "\"signalStrength\":" +
             String(WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : 0) + ",";
  payload += "\"firmwareVersion\":\"" + String(FIRMWARE_VERSION) + "\",";
  payload += "\"imageBase64\":\"" + imageBase64 + "\"";
  payload += "}";

  String url = String(FUNCTIONS_BASE_URL) + "/ingestCameraImage";
  int status = 0;
  String body;
  Serial.printf("[FB] Uploading JPEG %u bytes as %s\n",
                (unsigned)fb->len, DEVICE_ID);
  if (!https.postJsonBody(url.c_str(), DEVICE_ID, INGEST_KEY, payload, status, body)) {
    Serial.printf("[FB] Upload failed (code=%d %s)\n", status, body.c_str());
    return false;
  }
  Serial.printf("[FB] Camera ingest %d\n", status);
  return status >= 200 && status < 300;
}

bool captureAndUpload() {
  if (streamActive) return false;
  camera_fb_t* fb = captureStillWithFlash();
  if (!fb) {
    setFlash(false);
    Serial.println("[CAM] Still capture failed");
    return false;
  }
  const bool ok = uploadCapture(fb);
  esp_camera_fb_return(fb);
  drainCameraFrames(1);
  return ok;
}

void maybeUploadScheduled() {
  if (streamActive || WiFi.status() != WL_CONNECTED || !cameraReady) return;

  const unsigned long now = millis();
  if (!initialUploadDone && wifiConnectedAtMs > 0 &&
      now - wifiConnectedAtMs >= CAPTURE_FIRST_UPLOAD_DELAY_MS) {
    initialUploadDone = true;
    lastCaptureMs = now;
    Serial.println("[FB] First cloud capture...");
    cloudIntervalMs = captureAndUpload() ? CAPTURE_INTERVAL_MS : CAPTURE_RETRY_INTERVAL_MS;
    return;
  }

  if (initialUploadDone && now - lastCaptureMs >= cloudIntervalMs) {
    lastCaptureMs = now;
    Serial.println("[FB] Scheduled cloud capture...");
    const bool ok = captureAndUpload();
    cloudIntervalMs = ok ? CAPTURE_INTERVAL_MS : CAPTURE_RETRY_INTERVAL_MS;
    if (!ok) Serial.printf("[FB] Retry in %lu ms\n", cloudIntervalMs);
  }
}

void handleCapture(WiFiClient& client) {
  if (!cameraReady) {
    sendEmpty(client, "503 Service Unavailable");
    return;
  }

  camera_fb_t* fb = captureStillWithFlash();
  if (!fb) {
    sendEmpty(client, "500 Internal Server Error");
    return;
  }

  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: image/jpeg");
  client.println("Access-Control-Allow-Origin: *");
  client.println("Cache-Control: no-store");
  client.print("Content-Length: ");
  client.println(fb->len);
  client.println("Connection: close");
  client.println();
  client.write(fb->buf, fb->len);
  esp_camera_fb_return(fb);
  setFlash(false);
  drainCameraFrames(1);
  Serial.println("[CAM] /capture sent");
}

void handleStream(WiFiClient& client) {
  if (!cameraReady) {
    sendEmpty(client, "503 Service Unavailable");
    return;
  }

  setFlash(false);
  streamActive = true;
  client.setNoDelay(true);

  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: multipart/x-mixed-replace; boundary=frame");
  client.println("Access-Control-Allow-Origin: *");
  client.println("Cache-Control: no-cache, no-store, must-revalidate");
  client.println("Pragma: no-cache");
  client.println("Connection: close");
  client.println();

  Serial.println("[CAM] MJPEG stream started (cloud upload paused)");

  while (client.connected()) {
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      delay(1);
      continue;
    }

    const size_t frameLen = fb->len;
    client.printf(
        "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n",
        frameLen);
    const size_t written = client.write(fb->buf, frameLen);
    client.print("\r\n");
    esp_camera_fb_return(fb);

    if (written != frameLen) break;
  }

  streamActive = false;
  setFlash(false);
  // After stream, nudge next cloud capture soon so Clear → uploads resume quickly.
  if (initialUploadDone) {
    cloudIntervalMs = CAPTURE_RETRY_INTERVAL_MS;
    lastCaptureMs = millis() - cloudIntervalMs;
  }
  Serial.println("[CAM] MJPEG stream ended (cloud upload resumes soon)");
}

void handleClient(WiFiClient& client) {
  client.setNoDelay(true);
  client.setTimeout(2000);

  String req = client.readStringUntil('\r');
  while (client.connected() && client.available()) {
    String line = client.readStringUntil('\n');
    if (line == "\r" || line.length() <= 1) break;
  }

  Serial.printf("[HTTP] %s\n", req.c_str());

  if (req.indexOf("GET /stream") != -1) {
    handleStream(client);
  } else if (req.indexOf("GET /capture") != -1) {
    handleCapture(client);
  } else if (req.indexOf("GET /status") != -1) {
    handleStatus(client);
  } else {
    sendEmpty(client, "404 Not Found");
  }

  client.stop();
  if (streamActive) {
    streamActive = false;
    setFlash(false);
  }
}

void printBootUrls() {
  Serial.printf("[BOOT] Stream  : http://%s/stream\n",
                WiFi.localIP().toString().c_str());
  Serial.printf("[BOOT] Capture : http://%s/capture\n",
                WiFi.localIP().toString().c_str());
  Serial.printf("[BOOT] Status  : http://%s/status\n",
                WiFi.localIP().toString().c_str());
  Serial.printf("[BOOT] DeviceId: %s\n", DEVICE_ID);
}

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println("[BOOT] AgriScout ESP32-CAM Camera Node v2");
  Serial.printf("[BOOT] DEVICE_ID=%s\n", DEVICE_ID);
  Serial.printf("[BOOT] Capture interval=%lu ms\n", CAPTURE_INTERVAL_MS);

  setFlash(false);
  connectWiFi();
  initCamera();
  server.begin();
  Serial.println("[BOOT] HTTP server on port 80");
  if (WiFi.status() == WL_CONNECTED) {
    printBootUrls();
    bootInfoPrinted = true;
  }
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) {
    streamActive = false;
    setFlash(false);
    Serial.println("[WiFi] Lost — reconnecting...");
    WiFi.disconnect();
    connectWiFi();
    if (WiFi.status() != WL_CONNECTED) {
      delay(3000);
      return;
    }
    bootInfoPrinted = false;
  }

  if (!bootInfoPrinted && WiFi.status() == WL_CONNECTED) {
    printBootUrls();
    bootInfoPrinted = true;
  }

  WiFiClient client = server.available();
  if (client) {
    handleClient(client);
  } else {
    // Always drain one frame while idle — stops FB-OVF spam.
    drainCameraFrames(1);
    maybeUploadScheduled();
    delay(5);
  }
}
