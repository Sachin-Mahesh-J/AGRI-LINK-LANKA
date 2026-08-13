/*
 * ESP32-CAM - Smart School Camera Node
 *
 * Provides:
 *   GET /capture - JPEG frame for Python AI recognition
 *   GET /status  - JSON health for dashboard/testing
 *
 * AI-Thinker pin allocation kept from your original sketch.
 */

#include "esp_camera.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// =============================
// WiFi and backend
// =============================
const char* ssid = "BELL4G_0F3B";
const char* password = "1TYJ07D062F";
const char* backendURL = "http://192.168.8.107:8080/api";
const bool reportToBackend = false; // Keep camera server fast; dashboard can poll Python/AI.

// Keep the camera on a stable address. If your router uses another gateway,
// change gateway/subnet to match your WiFi network.
IPAddress localIP(192, 168, 8, 150);
IPAddress gateway(192, 168, 8, 1);
IPAddress subnet(255, 255, 255, 0);
IPAddress dns1(8, 8, 8, 8);

// =============================
// AI-Thinker ESP32-CAM pin map
// =============================
#define PWDN_GPIO_NUM   32
#define RESET_GPIO_NUM  -1
#define XCLK_GPIO_NUM    0
#define SIOD_GPIO_NUM   26
#define SIOC_GPIO_NUM   27
#define Y2_GPIO_NUM      5
#define Y3_GPIO_NUM     18
#define Y4_GPIO_NUM     19
#define Y5_GPIO_NUM     21
#define Y6_GPIO_NUM     36
#define Y7_GPIO_NUM     39
#define Y8_GPIO_NUM     34
#define Y9_GPIO_NUM     35
#define VSYNC_GPIO_NUM  25
#define HREF_GPIO_NUM   23
#define PCLK_GPIO_NUM   22

#define FLASH_GPIO_NUM   4

WiFiServer server(80);
bool cameraReady = false;
bool usingStaticIp = true;
unsigned long lastStatusReport = 0;
#define STATUS_REPORT_MS 15000UL

// =============================
// WiFi
// =============================
bool waitForWiFi(int maxAttempts) {
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < maxAttempts) {
        delay(500);
        Serial.print(".");
        attempts++;
    }
    return WiFi.status() == WL_CONNECTED;
}

void printNetworkInfo() {
    Serial.printf("\n[WiFi] Connected. IP: %s\n", WiFi.localIP().toString().c_str());
    Serial.printf("[WiFi] Gateway: %s\n", WiFi.gatewayIP().toString().c_str());
    Serial.printf("[WiFi] MAC: %s\n", WiFi.macAddress().c_str());
    Serial.printf("[WiFi] Mode: %s\n", usingStaticIp ? "static" : "dhcp");
    Serial.printf("[BOOT] Capture URL: http://%s/capture\n", WiFi.localIP().toString().c_str());
    Serial.printf("[BOOT] Status URL: http://%s/status\n", WiFi.localIP().toString().c_str());
}

void connectWiFi() {
    Serial.printf("[WiFi] Connecting to %s with static IP %s", ssid, localIP.toString().c_str());
    WiFi.mode(WIFI_STA);
    WiFi.setSleep(false);
    WiFi.setAutoReconnect(true);
    WiFi.config(localIP, gateway, subnet, dns1);
    WiFi.begin(ssid, password);

    usingStaticIp = true;
    if (waitForWiFi(24)) {
        printNetworkInfo();
        return;
    }

    Serial.println("\n[WiFi] Static IP failed. Trying DHCP...");
    WiFi.disconnect(true);
    delay(500);
    WiFi.config(INADDR_NONE, INADDR_NONE, INADDR_NONE);
    WiFi.begin(ssid, password);

    usingStaticIp = false;
    if (waitForWiFi(24)) {
        printNetworkInfo();
        return;
    }

    Serial.println("\n[WiFi] Failed to connect. Will retry in loop.");
}

// =============================
// Backend reporting
// =============================
void postJson(String path, String json) {
    if (!reportToBackend) return;
    if (WiFi.status() != WL_CONNECTED) return;

    HTTPClient http;
    http.begin(String(backendURL) + path);
    http.addHeader("Content-Type", "application/json");
    http.setTimeout(400);
    int code = http.POST(json);
    Serial.printf("[API] POST %s -> HTTP %d\n", path.c_str(), code);
    http.end();
}

void reportDeviceStatus() {
    String json = "{";
    json += "\"online\":\"" + String(cameraReady ? "online" : "error") + "\",";
    json += "\"deviceType\":\"esp32-camera\",";
    json += "\"ipAddress\":\"" + WiFi.localIP().toString() + "\",";
    json += "\"macAddress\":\"" + WiFi.macAddress() + "\",";
    json += "\"networkMode\":\"" + String(usingStaticIp ? "static" : "dhcp") + "\"";
    json += "}";
    postJson("/device/esp32-cam/status", json);
}

void reportEvent(const char* type, const char* message) {
    String json = "{";
    json += "\"type\":\"" + String(type) + "\",";
    json += "\"message\":\"" + String(message) + "\",";
    json += "\"source\":\"esp32-cam\"";
    json += "}";
    postJson("/events", json);
}

// =============================
// Camera
// =============================
void initCamera() {
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
    config.grab_mode = CAMERA_GRAB_LATEST;

    if (psramFound()) {
        Serial.println("[CAM] PSRAM found. Using stable QQVGA and fb_count=1");
        config.fb_count = 1;
        config.fb_location = CAMERA_FB_IN_PSRAM;
    } else {
        Serial.println("[CAM] No PSRAM. Using QQVGA and fb_count=1");
        config.fb_count = 1;
        config.fb_location = CAMERA_FB_IN_DRAM;
        config.frame_size = FRAMESIZE_QQVGA;
    }

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        Serial.printf("[CAM] Init failed: 0x%x\n", err);
        cameraReady = false;
        return;
    }

    cameraReady = true;
    Serial.println("[CAM] Camera initialized OK");

    sensor_t* s = esp_camera_sensor_get();
    if (s) {
        s->set_brightness(s, 1);
        s->set_contrast(s, 1);
        s->set_saturation(s, 0);
        s->set_sharpness(s, 1);
        s->set_whitebal(s, 1);
        s->set_awb_gain(s, 1);
        s->set_exposure_ctrl(s, 1);
    }
}

camera_fb_t* captureFrame(bool useFlash) {
    if (useFlash) {
        digitalWrite(FLASH_GPIO_NUM, HIGH);
        delay(80);
    }

    camera_fb_t* fb = esp_camera_fb_get();

    if (useFlash) {
        digitalWrite(FLASH_GPIO_NUM, LOW);
    }

    return fb;
}

// =============================
// HTTP responses
// =============================
void sendStatus(WiFiClient& client) {
    String body = "{";
    body += "\"device\":\"esp32-cam\",";
    body += "\"ip\":\"" + WiFi.localIP().toString() + "\",";
    body += "\"mac\":\"" + WiFi.macAddress() + "\",";
    body += "\"networkMode\":\"" + String(usingStaticIp ? "static" : "dhcp") + "\",";
    body += "\"cameraReady\":" + String(cameraReady ? "true" : "false") + ",";
    body += "\"psram\":" + String(psramFound() ? "true" : "false");
    body += "}";

    client.println("HTTP/1.1 200 OK");
    client.println("Content-Type: application/json");
    client.print("Content-Length: ");
    client.println(body.length());
    client.println("Connection: close");
    client.println();
    client.println(body);
}

void sendEmpty(WiFiClient& client, int code, const char* status) {
    client.print("HTTP/1.1 ");
    client.println(status);
    client.println("Content-Length: 0");
    client.println("Connection: close");
    client.println();
}

void sendText(WiFiClient& client, const char* body) {
    client.println("HTTP/1.1 200 OK");
    client.println("Content-Type: text/plain");
    client.println("Access-Control-Allow-Origin: *");
    client.print("Content-Length: ");
    client.println(strlen(body));
    client.println("Connection: close");
    client.println();
    client.print(body);
}

// =============================
// Setup
// =============================
void setup() {
    WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
    Serial.begin(115200);
    Serial.println("\n[BOOT] ESP32-CAM starting...");

    pinMode(FLASH_GPIO_NUM, OUTPUT);
    digitalWrite(FLASH_GPIO_NUM, LOW);

    connectWiFi();
    initCamera();
    server.begin();
    Serial.println("[BOOT] HTTP server started on port 80");

    reportEvent("boot", "ESP32-CAM booted");
    reportDeviceStatus();
}

// =============================
// Main loop
// =============================
void loop() {
    if (WiFi.status() != WL_CONNECTED) {
        Serial.println("[WiFi] Connection lost. Reconnecting...");
        WiFi.disconnect();
        connectWiFi();
        if (WiFi.status() != WL_CONNECTED) {
            delay(5000);
            return;
        }
        reportDeviceStatus();
    }

    WiFiClient client = server.available();
    if (!client) {
        unsigned long nowMs = millis();
        if (nowMs - lastStatusReport >= STATUS_REPORT_MS) {
            reportDeviceStatus();
            lastStatusReport = nowMs;
        }
        delay(5);
        return;
    }

    client.setNoDelay(true);
    client.setTimeout(1000);
    String req = client.readStringUntil('\r');
    while (client.connected() && client.available()) {
        String line = client.readStringUntil('\n');
        if (line == "\r" || line.length() <= 1) break;
    }

    Serial.printf("[HTTP] Request: %s\n", req.c_str());

    if (req.indexOf("/ping") != -1) {
        sendText(client, "pong");

    } else if (req.indexOf("/capture") != -1) {
        if (!cameraReady) {
            sendEmpty(client, 500, "500 Internal Server Error");
            client.stop();
            return;
        }

        camera_fb_t* fb = captureFrame(false);
        if (!fb) {
            Serial.println("[CAM] Frame capture failed");
            sendEmpty(client, 500, "500 Internal Server Error");
            client.stop();
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
        size_t frameLen = fb->len;
        client.write(fb->buf, frameLen);

        esp_camera_fb_return(fb);
        Serial.printf("[CAM] Frame sent (%u bytes)\n", frameLen);

    } else if (req.indexOf("/status") != -1) {
        sendStatus(client);

    } else {
        sendEmpty(client, 404, "404 Not Found");
    }

    client.stop();
}
