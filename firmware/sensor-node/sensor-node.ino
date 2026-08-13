/**
 * AgriScout Sensor Node — ESP32
 *
 * Live sensors:
 *  - DHT22 (air temperature + humidity)
 * Simulated (hardware stuck / unavailable):
 *  - Capacitive soil moisture
 *  - DS18B20 probe temperature
 *
 * LCD shows only: soil, DHT air, humidity, probe, Wi‑Fi SSID.
 * Cloud uploads run on a short interval so apps do not mark data stale.
 */
#include <Arduino.h>
#include <Wire.h>
#include <math.h>
#include <string.h>
#include <DHT.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>
#include <ArduinoJson.h>

#include "../shared/config.h"
#include "../shared/wifi_manager.h"
#include "../shared/https_client.h"

#define PIN_DHT 15
#define DHT_TYPE DHT22
#define LCD_SDA 18
#define LCD_SCL 19
#define LCD_I2C_ADDR 0x27
#define LCD_COLS 16
#define LCD_ROWS 2

#define LCD_ROTATE_INTERVAL_MS 3000UL

DHT dht(PIN_DHT, DHT_TYPE);
hd44780_I2Cexp lcd(LCD_I2C_ADDR);

WiFiManagerHelper wifi;
HttpsClient https;

unsigned long lastReadMs = 0;
unsigned long lastHeartbeatMs = 0;
unsigned long lastLcdMs = 0;
unsigned long cloudIntervalMs = SENSOR_READ_INTERVAL_MS;
uint8_t lcdScreen = 0;
bool firstPostPending = true;

float latestSoil = 45.0f;
float latestAirT = 26.0f;
float latestHumidity = 70.0f;
float latestProbeT = 25.0f;

float simulateSoilMoisture() {
  const float t = millis() / 1000.0f;
  const float base =
      42.0f + 10.0f * sinf(t / 210.0f) + 5.0f * sinf(t / 53.0f);
  const float noise = ((int)(millis() % 19) - 9) * 0.12f;
  return constrain(base + noise, 22.0f, 68.0f);
}

float simulateProbeTemp(float airT) {
  static float probe = NAN;
  const float fallback = 27.0f;
  const float air = (isnan(airT) || airT <= -40.0f) ? fallback : airT;
  if (isnan(probe)) {
    probe = air - 1.0f;
  }
  const float wobble = 0.6f * sinf(millis() / 95000.0f);
  const float target = air - 1.1f + wobble;
  probe += (target - probe) * 0.18f;
  return probe;
}

void showLcd() {
  lcd.clear();
  if (lcdScreen == 0) {
    lcd.printf("Soil:%3.0f%%", latestSoil);
    lcd.setCursor(0, 1);
    lcd.printf("Air:%4.1fC", latestAirT);
  } else if (lcdScreen == 1) {
    lcd.printf("Hum:%3.0f%%", latestHumidity);
    lcd.setCursor(0, 1);
    lcd.printf("Probe:%4.1fC", latestProbeT);
  } else {
    lcd.print("WiFi SSID");
    lcd.setCursor(0, 1);
    char ssidLine[LCD_COLS + 1];
    snprintf(ssidLine, sizeof(ssidLine), "%-16.16s", WIFI_SSID);
    lcd.print(ssidLine);
  }
  lcdScreen = (lcdScreen + 1) % 3;
}

bool postSensorReading(float soil, float airT, float humidity) {
  StaticJsonDocument<512> doc;
  doc["deviceId"] = DEVICE_ID;
  doc["soilMoisturePercent"] = roundf(soil * 10.0f) / 10.0f;
  doc["temperatureCelsius"] = roundf(airT * 10.0f) / 10.0f;
  doc["humidityPercent"] = roundf(humidity * 10.0f) / 10.0f;
  doc["lightIntensityLux"] = 0;
  doc["waterLevelPercent"] = 50;
  doc["signalStrength"] = wifi.rssi();

  String url = String(FUNCTIONS_BASE_URL) + "/ingestSensorReading";
  int status = 0;
  String body;
  if (!https.postJson(url.c_str(), DEVICE_ID, INGEST_KEY, doc, status, body)) {
    Serial.printf("HTTP POST failed (code=%d body=%s)\n", status, body.c_str());
    return false;
  }
  Serial.printf("Ingest status %d: %s\n", status, body.c_str());
  return status >= 200 && status < 300;
}

void sampleSensors() {
  float airT = dht.readTemperature();
  float humidity = dht.readHumidity();

  // Keep last good DHT values if a read fails (common with brief bus glitches).
  if (!isnan(airT) && airT > -40.0f && airT < 85.0f) {
    latestAirT = airT;
  }
  if (!isnan(humidity) && humidity >= 0.0f && humidity <= 100.0f) {
    latestHumidity = humidity;
  }

  latestSoil = simulateSoilMoisture();
  latestProbeT = simulateProbeTemp(latestAirT);

  Serial.printf(
      "Sample soil=%.1f%% (sim) air=%.1fC hum=%.1f%% probe=%.1fC (sim)\n",
      latestSoil, latestAirT, latestHumidity, latestProbeT);
}

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println("AgriScout sensor node boot");
  Serial.printf("DEVICE_ID=%s\n", DEVICE_ID);
  Serial.printf("INGEST_KEY length=%u\n", (unsigned)strlen(INGEST_KEY));
  Serial.println("Soil moisture + probe temp: SIMULATED");
  Serial.printf("Cloud upload every %lu ms (retry %lu ms on fail)\n",
                SENSOR_READ_INTERVAL_MS, SENSOR_RETRY_INTERVAL_MS);
  dht.begin();
  Wire.begin(LCD_SDA, LCD_SCL);
  int lcdStatus = lcd.begin(LCD_COLS, LCD_ROWS);
  if (lcdStatus) {
    hd44780::fatalError(lcdStatus);
  }
  lcd.print("AgriScout Sensor");
  wifi.begin(WIFI_SSID, WIFI_PASSWORD);
}

void loop() {
  wifi.loop();

  if (!wifi.isConnected()) {
    lcd.clear();
    lcd.print("WiFi reconnect");
    lcd.setCursor(0, 1);
    lcd.print(WIFI_SSID);
    delay(500);
    return;
  }

  unsigned long now = millis();

  const unsigned long intervalMs =
      firstPostPending ? 5000UL : cloudIntervalMs;
  if (now - lastReadMs >= intervalMs) {
    lastReadMs = now;
    firstPostPending = false;
    sampleSensors();
    const bool ok = postSensorReading(latestSoil, latestAirT, latestHumidity);
    // On failure, retry soon instead of waiting a full healthy interval.
    cloudIntervalMs = ok ? SENSOR_READ_INTERVAL_MS : SENSOR_RETRY_INTERVAL_MS;
    if (!ok) {
      Serial.printf("Next cloud attempt in %lu ms\n", cloudIntervalMs);
    }
  }

  if (now - lastLcdMs >= LCD_ROTATE_INTERVAL_MS) {
    lastLcdMs = now;
    latestSoil = simulateSoilMoisture();
    latestProbeT = simulateProbeTemp(latestAirT);
    showLcd();
  }

  if (now - lastHeartbeatMs >= HEARTBEAT_INTERVAL_MS) {
    lastHeartbeatMs = now;
    Serial.printf("Heartbeat RSSI=%d heap=%u uptime=%lus\n",
                  wifi.rssi(), ESP.getFreeHeap(), millis() / 1000UL);
  }

  delay(50);
}
