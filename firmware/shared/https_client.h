#pragma once

#include <HTTPClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

class HttpsClient {
 public:
  bool postJson(const char* url,
                const char* deviceId,
                const char* ingestKey,
                JsonDocument& doc,
                int& statusCode,
                String& responseBody) {
    String payload;
    serializeJson(doc, payload);
    return postJsonBody(url, deviceId, ingestKey, payload, statusCode, responseBody);
  }

  bool postJsonBody(const char* url,
                    const char* deviceId,
                    const char* ingestKey,
                    const String& payload,
                    int& statusCode,
                    String& responseBody) {
    for (int attempt = 1; attempt <= 2; attempt++) {
      if (attempt > 1) {
        Serial.println("HTTPS retry…");
        delay(400);
      }
      if (postOnce(url, deviceId, ingestKey, payload, statusCode, responseBody)) {
        return true;
      }
    }
    return false;
  }

 private:
  IPAddress cachedHostIp_;
  bool hasCachedHostIp_ = false;
  String cachedHostName_;

  static bool isUsableIp(const IPAddress& ip) {
    return !(ip[0] == 0 && ip[1] == 0 && ip[2] == 0 && ip[3] == 0);
  }

  static bool parseHttpsUrl(const char* url, String& host, String& path) {
    if (!url) return false;
    const char* scheme = strstr(url, "://");
    if (!scheme) return false;
    const char* hostStart = scheme + 3;
    const char* pathStart = strchr(hostStart, '/');
    if (!pathStart) {
      host = String(hostStart);
      path = "/";
      return host.length() > 0;
    }
    host = String(hostStart).substring(0, pathStart - hostStart);
    path = String(pathStart);
    return host.length() > 0 && path.length() > 0;
  }

  bool resolveHost(const char* host, IPAddress& out) {
    if (hasCachedHostIp_ && cachedHostName_ == host && isUsableIp(cachedHostIp_)) {
      out = cachedHostIp_;
      return true;
    }

    if (WiFi.hostByName(host, out) && isUsableIp(out)) {
      cachedHostIp_ = out;
      cachedHostName_ = host;
      hasCachedHostIp_ = true;
      return true;
    }

    const IPAddress local = WiFi.localIP();
    const IPAddress gateway = WiFi.gatewayIP();
    const IPAddress subnet = WiFi.subnetMask();
    WiFi.config(local, gateway, subnet, IPAddress(8, 8, 8, 8), IPAddress(1, 1, 1, 1));
    delay(250);

    if (WiFi.hostByName(host, out) && isUsableIp(out)) {
      cachedHostIp_ = out;
      cachedHostName_ = host;
      hasCachedHostIp_ = true;
      return true;
    }

    hasCachedHostIp_ = false;
    cachedHostName_ = "";
    out = IPAddress(0, 0, 0, 0);
    return false;
  }

  bool postOnce(const char* url,
                const char* deviceId,
                const char* ingestKey,
                const String& payload,
                int& statusCode,
                String& responseBody) {
    statusCode = 0;
    responseBody = "";

    String host;
    String path;
    if (!parseHttpsUrl(url, host, path)) {
      Serial.println("HTTPS URL parse failed");
      statusCode = -1;
      responseBody = "bad url";
      return false;
    }

    Serial.printf("HTTPS POST %s%s (%u bytes)\n",
                  host.c_str(), path.c_str(), (unsigned)payload.length());
    Serial.printf("Free heap before TLS: %u\n", ESP.getFreeHeap());

    IPAddress resolved;
    if (!resolveHost(host.c_str(), resolved)) {
      statusCode = -1;
      responseBody = "DNS failed";
      Serial.println("DNS failed — cannot reach Cloud Functions host");
      return false;
    }
    Serial.printf("DNS OK -> %s\n", resolved.toString().c_str());

    WiFiClientSecure client;
    client.setInsecure();
    client.setHandshakeTimeout(45);
    client.setTimeout(45);

    HTTPClient http;
    http.setConnectTimeout(20000);
    // Camera JPEG base64 payloads need a longer write/read window.
    http.setTimeout(45000);
    http.setReuse(false);

    if (!http.begin(client, host.c_str(), 443, path.c_str(), true)) {
      Serial.println("HTTP begin() failed");
      hasCachedHostIp_ = false;
      return false;
    }

    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-Device-Id", deviceId);
    http.addHeader("Authorization", String("Bearer ") + ingestKey);
    http.addHeader("Connection", "close");

    statusCode = http.POST(payload);
    if (statusCode > 0) {
      responseBody = http.getString();
    } else {
      responseBody = http.errorToString(statusCode);
      Serial.printf("TLS/HTTP transport error %d: %s\n",
                    statusCode, responseBody.c_str());
      hasCachedHostIp_ = false;
    }
    http.end();
    client.stop();
    Serial.printf("Free heap after TLS: %u\n", ESP.getFreeHeap());
    return statusCode > 0;
  }
};
