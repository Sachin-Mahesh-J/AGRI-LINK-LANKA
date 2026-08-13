# Agri Link Lanka

**Agri Link Lanka** is a smart-agriculture platform for Sri Lanka’s farm value chain. It connects field officers, administrators, input suppliers and produce buyers on one system: farm records, IoT monitoring, AI crop-health support, inventory, and harvest marketplace.

The codebase name in this repository is **AgriScout**. Android package: `com.example.agriscout`. Firebase project used by the current configs: `agriscout-4586c`.

---

## What the system does

Field officers use the **Android app** (including offline) to register farms, log visits and crop reports with GPS and photos, review IoT readings, run on-device plant-disease AI, follow crop-calendar recommendations, request inputs, and list harvests.

Administrators use the **web dashboard** to approve users, assign farms, register IoT devices, manage inventory, review alerts and reports, and oversee the marketplace.

**ESP32 sensor** and **ESP32-CAM** nodes send soil/climate readings and farm images to Firebase over HTTPS. **Cloud Functions** ingest that data, store it in Firestore/Storage, and send notifications (inventory, marketplace, low stock).

**AI** runs on the phone (TensorFlow Lite disease model + crop calendars and agronomic rules). Training data and notes live under `ml/`.

```
ESP32 sensor / ESP32-CAM  ──HTTPS──►  Cloud Functions  ──►  Firestore / Storage
                                                              ▲
Android app (field officer)  ◄──── Firebase Auth / FCM ───────┤
Admin dashboard (React)      ◄──── Firebase Web SDK ──────────┘
```

| Role | Where | Typical work |
|------|--------|----------------|
| Field officer | Android | Farms, visits, reports, IoT, AI, inventory requests, harvest listings |
| Admin / super admin | Web dashboard | Users, farms, IoT registry, stock, alerts, analytics |
| Supplier | Web marketplace | Input catalogue and product requests |
| Buyer | Web harvest portal | Browse listings and submit harvest requests |

---

## Repository layout

```
.
├── app/                 Android app (Kotlin, Jetpack Compose)
├── admin-dashboard/     React 19 + Vite admin / marketplace UI
├── functions/           Firebase Cloud Functions (TypeScript, Node 20)
├── firmware/
│   ├── sensor-node/     ESP32 sensor + LCD
│   ├── camera-node/     ESP32-CAM stream + cloud upload
│   └── shared/          Wi-Fi, device ID, ingest URL
├── ml/                  PlantVillage dataset + training notes
├── firebase.json
├── firestore.rules
├── storage.rules
└── firestore.indexes.json
```

---

## Prerequisites

Install only what you need for the parts you will run.

| Component | You need |
|-----------|----------|
| Android app | [Android Studio](https://developer.android.com/studio), JDK 17 (Studio’s bundled JDK is fine), Android SDK. Phone or emulator with **API 30+** |
| Admin dashboard | [Node.js 20+](https://nodejs.org/) and npm |
| Cloud Functions | Node.js **20**, Firebase CLI (`npm i -g firebase-tools`) |
| Firmware | Arduino IDE (ESP32 board package) or [PlatformIO](https://platformio.org/) |
| AI retraining | Python 3.11+ (optional; the app already ships a `.tflite` model) |
| Backend | A [Firebase](https://console.firebase.google.com/) project with **Authentication (Email/Password)**, **Firestore**, **Storage**, and **Functions** |

Clone the repo:

```bash
git clone https://github.com/Sachin-Mahesh-J/AGRI-LINK-LANKA.git
cd AGRI-LINK-LANKA
```

---

## 1. Firebase (do this first)

The apps talk to Firebase. You can keep using project `agriscout-4586c` if you have access, or create your own project.

1. In Firebase Console, enable **Authentication → Email/Password**, **Cloud Firestore**, and **Storage**.
2. Register an **Android** app with package `com.example.agriscout` and download `google-services.json` into `app/` (this file is already in the repo for `agriscout-4586c`).
3. Register a **Web** app (do not reuse the Android app ID). Copy the web config into the dashboard env file (next section).
4. Log in and select the project:

```bash
firebase login
firebase use agriscout-4586c
```

If you created a new project, change `.firebaserc` to that project ID, replace `app/google-services.json`, and use that ID everywhere below.

Deploy rules, indexes, and functions:

```bash
cd functions
npm install
npm run build
cd ..
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

Functions region is `us-central1`. After deploy you should have at least:

- `https://us-central1-<PROJECT_ID>.cloudfunctions.net/ingestSensorReading`
- `https://us-central1-<PROJECT_ID>.cloudfunctions.net/ingestCameraImage`

### First admin user

1. Authentication → Add user (email + password), copy the UID.
2. Firestore → create `userAccess/{uid}`:

```json
{
  "role": "admin",
  "status": "active",
  "assignedFarmIds": []
}
```

Field officers who register in the Android app start as `field_officer` with `status: pending`. An admin must set `status` to `active` (Users page on the dashboard) before they can use the app.

Optional demo data (creates sample users, farms, inventory):

```bash
cd admin-dashboard
npm install
npm run seed
```

Default seed logins (only if you run seed): `admin@agriscout.demo` / `Admin123!` and `officer1@agriscout.demo` / `Officer123!`. Seed needs a Firebase service-account JSON saved as `admin-dashboard/service-account.json` (see comments at the top of `admin-dashboard/scripts/seed-firestore.mjs`). Do not commit that JSON file.

---

## 2. Admin dashboard

```bash
cd admin-dashboard
copy .env.example .env.local
```

On macOS/Linux use `cp .env.example .env.local`.

Edit `.env.local` with the **Web** app values from Firebase → Project settings → Your apps:

```
VITE_FIREBASE_API_KEY=
VITE_FIREBASE_AUTH_DOMAIN=agriscout-4586c.firebaseapp.com
VITE_FIREBASE_PROJECT_ID=agriscout-4586c
VITE_FIREBASE_STORAGE_BUCKET=agriscout-4586c.firebasestorage.app
VITE_FIREBASE_MESSAGING_SENDER_ID=
VITE_FIREBASE_APP_ID=
```

`VITE_FIREBASE_APP_ID` must be the **web** app ID (`…:web:…`), not the Android one.

```bash
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173) and sign in as an **active** admin.

| Page | Purpose |
|------|---------|
| Overview | KPIs |
| Users | Approve officers, assign farms |
| Farms | Cross-officer farm list |
| IoT | Register sensor/camera devices, view readings and captures |
| Inventory | Stock in/out, approve requests |
| Marketplace / Harvest | Supplier products and buyer harvest listings |
| Alerts / Reports | Low stock, operations, analytics |

To publish the dashboard to Firebase Hosting:

```bash
npm run build
cd ..
firebase deploy --only hosting
```

---

## 3. Android field-officer app

1. Open this repository folder in **Android Studio**.
2. Edit `local.properties` so `sdk.dir` points at **your** Android SDK (the committed path is machine-specific).

```
sdk.dir=C:\\Users\\<You>\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=your-google-maps-key
WEATHER_API_KEY=your-openweather-key
```

Maps and weather still build if those keys are empty; map tiles and live weather will not work until you add them.

3. Confirm `app/google-services.json` matches your Firebase Android app.
4. Sync Gradle, then Run on a device/emulator (min SDK 30).

Command line:

```bash
./gradlew assembleDebug
```

On Windows: `gradlew.bat assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.

**First run**

1. Register with email/password in the app.
2. On the admin dashboard, open **Users** and approve the officer (`status: active`).
3. Sign in on the phone. Create a farm, take a crop-report photo, tap **Analyze** for on-device disease detection, then use **Sync** when you have internet.

The phone does not need to be on the same Wi-Fi as the ESP32 boards. Live device readings appear after ingest into Firestore and a successful sync.

---

## 4. ESP32 firmware

Use **two** devices with **two** registrations. Never flash the same `DEVICE_ID` / ingest key onto both boards.

1. Create a farm for an approved officer (Android or dashboard).
2. Dashboard → **IoT** → register a **sensor** device: copy Device ID and ingest key; link officer + farm.
3. Register a **camera** device the same way (`deviceType` = camera).
4. Edit `firmware/shared/config.h` **per board** before upload:

```c
#define WIFI_SSID "YourWifi"
#define WIFI_PASSWORD "YourPassword"
#define DEVICE_ID "ESP32-SENSOR-001"   // unique; must match IoT registry
#define INGEST_KEY "key-from-dashboard"
#define FUNCTIONS_BASE_URL "https://us-central1-agriscout-4586c.cloudfunctions.net"
```

Change `DEVICE_ID` and `INGEST_KEY` again when you flash the camera.

### Sensor node (ESP32 Dev Module)

**Arduino IDE:** install ESP32 (Espressif), then libraries DHT, OneWire, DallasTemperature, **hd44780**, ArduinoJson. Open `firmware/sensor-node/sensor-node.ino`. Board: **ESP32 Dev Module**. Serial: 115200.

**PlatformIO:**

```bash
cd firmware/sensor-node
pio run -t upload
```

Wiring used by the sketch:

| Hardware | GPIO |
|----------|------|
| DHT22 DATA | 15 |
| LCD I2C SDA | 18 |
| LCD I2C SCL | 19 |
| LCD address | `0x27` |

Soil moisture and probe temperature are currently **simulated** in firmware; DHT22 air temperature/humidity are live. After a successful ingest, the dashboard IoT page should show the device online.

### Camera node (AI-Thinker ESP32-CAM)

Open `firmware/camera-node/camera-node.ino`. Board: **AI Thinker ESP32-CAM**. Put GPIO0 to GND to enter flash mode, upload, then release GPIO0 and reset.

```bash
cd firmware/camera-node
pio run -t upload
```

On the same LAN as the camera:

| URL | Purpose |
|-----|---------|
| `http://<camera-ip>/stream` | Live MJPEG (LAN only) |
| `http://<camera-ip>/capture` | Single JPEG |
| `http://<camera-ip>/status` | Health JSON + `deviceId` |

Cloud JPEG upload uses `ingestCameraImage`. Live `/stream` is local HTTP only; the dashboard does not proxy the stream.

More firmware notes: [`firmware/README.md`](firmware/README.md).

---

## 5. AI module

The Android app already includes:

- `app/src/main/assets/models/plant_disease.tflite` — on-device leaf-disease classifier
- `app/src/main/assets/models/labels.txt` — class names
- `app/src/main/assets/crop_calendars/*.json` — rice, maize, tomato, wheat (and default)
- `app/src/main/assets/detection_rules.json` — symptom / rule fusion

You do **not** need Python to run the app. Retraining is optional.

```bash
cd ml
python -m venv .venv
# Windows:
.venv\Scripts\activate
# macOS / Linux:
source .venv/bin/activate
pip install -r requirements.txt
```

PlantVillage images used for Sri Lanka-relevant classes (maize, tomato, potato, strawberry, pepper) are under `ml/datasets/PlantVillage/`. See [`ml/README.md`](ml/README.md) and [`ml/datasets/README.md`](ml/datasets/README.md). Copy a newly exported `.tflite` into `app/src/main/assets/models/` if you retrain.

On the phone: open a crop report, attach a leaf photo, tap **Analyze**. Confidence and treatment guidance come from the TFLite model plus the rule/calendar engines.

---

## 6. Tests (optional)

```bash
cd functions
npm test

cd ../admin-dashboard
npm test
```

Android: run instrumented tests from Android Studio, or `./gradlew connectedAndroidTest` with a device attached.

---

## Typical first-time path

1. Firebase Auth + Firestore + Storage + deploy functions and rules.
2. Create an admin `userAccess` document (or run `npm run seed`).
3. `admin-dashboard`: `.env.local` → `npm install` → `npm run dev`.
4. Android Studio: fix `sdk.dir` → run the app → register officer → approve on the dashboard.
5. Register IoT devices, edit `firmware/shared/config.h`, flash sensor and camera separately.
6. Confirm readings/captures on the IoT page, then Sync on the phone.

---

## Notes

- Do not commit `.env.local`, service-account JSON, or production ingest keys.
- `firmware/shared/config.h` currently holds Wi-Fi and device credentials used for development — change them before you flash your own boards.
- Field officers stay `pending` until an admin activates them.
- Ingest returns **409** if the IoT device is not linked to `officerUid`, `farmId`, and `farmPath`.
- This repository is an HND / NIBM Software Engineering final project (Agri Link Lanka).
