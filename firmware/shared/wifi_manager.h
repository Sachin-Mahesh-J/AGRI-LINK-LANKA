#pragma once

#include <WiFi.h>

class WiFiManagerHelper {
 public:
  void begin(const char* ssid, const char* password) {
    ssid_ = ssid;
    password_ = password;
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.persistent(true);
    wasConnected_ = false;
    connect();
  }

  void loop() {
    const bool connected = WiFi.status() == WL_CONNECTED;
    if (connected && !wasConnected_) {
      applyPublicDns();
      logConnected();
    }
    wasConnected_ = connected;

    if (connected) return;
    if (millis() - lastAttemptMs_ < 5000UL) return;
    connect();
  }

  bool isConnected() const { return WiFi.status() == WL_CONNECTED; }
  int rssi() const { return isConnected() ? WiFi.RSSI() : 0; }

 private:
  const char* ssid_ = "";
  const char* password_ = "";
  unsigned long lastAttemptMs_ = 0;
  bool wasConnected_ = false;

  void connect() {
    lastAttemptMs_ = millis();
    Serial.printf("Connecting to WiFi \"%s\"...\n", ssid_);
    WiFi.begin(ssid_, password_);
  }

  // Many ISP/4G router DNS servers return bogus answers (0.0.0.0) for
  // cloudfunctions.net. Pin Google + Cloudflare DNS after DHCP lease.
  void applyPublicDns() {
    const IPAddress local = WiFi.localIP();
    const IPAddress gateway = WiFi.gatewayIP();
    const IPAddress subnet = WiFi.subnetMask();
    const IPAddress dns1(8, 8, 8, 8);
    const IPAddress dns2(1, 1, 1, 1);
    if (!WiFi.config(local, gateway, subnet, dns1, dns2)) {
      Serial.println("Warning: WiFi.config DNS override failed");
      return;
    }
    Serial.printf("DNS servers set to %s / %s\n",
                  dns1.toString().c_str(), dns2.toString().c_str());
  }

  void logConnected() {
    Serial.println();
    Serial.println("WiFi connected.");
    Serial.printf("  Network : %s\n", ssid_);
    Serial.printf("  IP      : %s\n", WiFi.localIP().toString().c_str());
    Serial.printf("  Gateway : %s\n", WiFi.gatewayIP().toString().c_str());
    Serial.printf("  DNS     : %s\n", WiFi.dnsIP().toString().c_str());
    Serial.printf("  RSSI    : %d dBm\n", WiFi.RSSI());
    Serial.println();
  }
};
