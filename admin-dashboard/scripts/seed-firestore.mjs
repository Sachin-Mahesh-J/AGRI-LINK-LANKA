/**
 * Bulk-seed AgriScout demo data into Firestore (and optional Auth users).
 *
 * Setup (one time) — pick ONE auth method:
 *
 *   Method A (recommended): Service account JSON key
 *     1. Firebase Console → Project settings → Service accounts
 *        Copy the "Firebase service account" email shown on that page.
 *     2. Open Google Cloud Console (link on same page, or):
 *        https://console.cloud.google.com/iam-admin/serviceaccounts?project=agriscout-4586c
 *     3. Click that firebase-adminsdk… service account → Keys tab
 *        → Add key → Create new key → JSON → Create
 *     4. Save the downloaded file as admin-dashboard/service-account.json
 *
 *   Method B: Application Default Credentials (no JSON file)
 *     1. Install Google Cloud CLI: https://cloud.google.com/sdk/docs/install
 *     2. Run: gcloud auth application-default login
 *     3. Run: npm run seed
 *
 *   Then: cd admin-dashboard && npm install && npm run seed
 *
 * Reset + seed (wipes Firestore first):
 *   npm run seed:reset
 *   — or — SEED_RESET=true SEED_CONFIRM=yes npm run seed
 *
 * Options (env vars):
 *   GOOGLE_APPLICATION_CREDENTIALS  path to service account JSON (default: ./service-account.json)
 *   FIREBASE_PROJECT_ID             override project id (default: agriscout-4586c)
 *   SEED_RESET                      "true" or pass --reset to delete existing Firestore data first
 *   SEED_CONFIRM                    must be "yes" when SEED_RESET is enabled (safety guard)
 *   SEED_CREATE_AUTH_USERS          "true" to create demo auth users (default: true)
 *   SEED_ADMIN_EMAIL                default admin@agriscout.demo
 *   SEED_ADMIN_PASSWORD             default Admin123!
 *   SEED_OFFICER1_EMAIL             default officer1@agriscout.demo
 *   SEED_OFFICER1_PASSWORD          default Officer123!
 *   SEED_OFFICER2_EMAIL             default officer2@agriscout.demo
 *   SEED_OFFICER2_PASSWORD          default Officer123!
 */

import { readFileSync, existsSync, readdirSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import admin from 'firebase-admin'

const __dirname = dirname(fileURLToPath(import.meta.url))
const rootDir = resolve(__dirname, '..')

const projectId = process.env.FIREBASE_PROJECT_ID ?? 'agriscout-4586c'

function findServiceAccountFile() {
  if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    return resolve(rootDir, process.env.GOOGLE_APPLICATION_CREDENTIALS)
  }

  const preferred = resolve(rootDir, 'service-account.json')
  if (existsSync(preferred)) return preferred

  const downloaded = readdirSync(rootDir)
    .filter((name) => name.endsWith('.json') && name.startsWith(`${projectId}-`))
    .map((name) => resolve(rootDir, name))
    .find((path) => {
      try {
        const parsed = JSON.parse(readFileSync(path, 'utf8'))
        return parsed.type === 'service_account'
      } catch {
        return false
      }
    })

  return downloaded ?? preferred
}

const serviceAccountPath = findServiceAccountFile()

function initializeFirebaseAdmin() {
  if (existsSync(serviceAccountPath)) {
    const serviceAccount = JSON.parse(readFileSync(serviceAccountPath, 'utf8'))
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId,
    })
    console.log(`Using service account file: ${serviceAccountPath}`)
    return
  }

  try {
    admin.initializeApp({
      credential: admin.credential.applicationDefault(),
      projectId,
    })
    console.log('Using Application Default Credentials (gcloud auth application-default login)')
    return
  } catch {
    // fall through to error below
  }

  console.error(`
Could not authenticate for seeding.

Option A — download a JSON key from Google Cloud Console:
  1. Firebase Console → Project settings → Service accounts
     Copy the firebase-adminsdk… service account email.
  2. Open: https://console.cloud.google.com/iam-admin/serviceaccounts?project=${projectId}
  3. Click that service account → Keys → Add key → Create new key → JSON
  4. Save as: admin-dashboard/service-account.json
  5. Run: npm run seed

Option B — use Google Cloud CLI instead of a JSON file:
  1. gcloud auth application-default login
  2. npm run seed

Note: The Firebase Console now shows Admin SDK code snippets instead of
"Generate new private key". Key download moved to Google Cloud Console.
`)
  process.exit(1)
}

initializeFirebaseAdmin()

const db = admin.firestore()
const auth = admin.auth()
const { Timestamp } = admin.firestore

const ts = (iso) => Timestamp.fromDate(new Date(iso))
const ms = (value) => value

const shouldReset =
  process.env.SEED_RESET === 'true' || process.argv.includes('--reset')
const createAuthUsers = process.env.SEED_CREATE_AUTH_USERS !== 'false'

/** Known AgriScout root collections (also discovers any others via listCollections). */
const KNOWN_ROOT_COLLECTIONS = [
  'userAccess',
  'staffProfiles',
  'users',
  'inventoryItems',
  'suppliers',
  'supplierProducts',
  'productRequests',
  'buyers',
  'harvestListings',
  'harvestRequests',
  'farmAssignments',
  'iotDevices',
  'stockTransactions',
  'alerts',
  'recommendations',
  'diseaseCatalog',
  'weatherWarnings',
]

async function wipeFirestore() {
  if (process.env.SEED_CONFIRM !== 'yes') {
    console.error(`
Refusing to wipe Firestore without confirmation.

To delete all existing Firestore data and re-seed, run:
  npm run seed:reset

Or set both env vars:
  SEED_RESET=true SEED_CONFIRM=yes npm run seed
`)
    process.exit(1)
  }

  const listed = await db.listCollections()
  const collectionIds = [
    ...new Set([...KNOWN_ROOT_COLLECTIONS, ...listed.map((col) => col.id)]),
  ].sort()

  console.log(`Wiping ${collectionIds.length} root collection(s)...`)
  for (const collectionId of collectionIds) {
    console.log(`  Deleting /${collectionId} (recursive)...`)
    await db.recursiveDelete(db.collection(collectionId))
  }
  console.log('Firestore wipe complete.\n')
}
const users = {
  admin: {
    email: process.env.SEED_ADMIN_EMAIL ?? 'admin@agriscout.demo',
    password: process.env.SEED_ADMIN_PASSWORD ?? 'Admin123!',
    displayName: 'Sachin Admin',
    phone: '+94771234567',
    role: 'admin',
  },
  officer1: {
    email: process.env.SEED_OFFICER1_EMAIL ?? 'officer1@agriscout.demo',
    password: process.env.SEED_OFFICER1_PASSWORD ?? 'Officer123!',
    displayName: 'Kamal Perera',
    phone: '+94771112233',
    role: 'field_officer',
  },
  officer2: {
    email: process.env.SEED_OFFICER2_EMAIL ?? 'officer2@agriscout.demo',
    password: process.env.SEED_OFFICER2_PASSWORD ?? 'Officer123!',
    displayName: 'Nimal Fernando',
    phone: '+94774445566',
    role: 'field_officer',
  },
}

async function ensureAuthUser({ email, password, displayName }) {
  try {
    const existing = await auth.getUserByEmail(email)
    console.log(`  Auth user exists: ${email} (${existing.uid})`)
    return existing.uid
  } catch (error) {
    if (error.code !== 'auth/user-not-found') throw error
    const created = await auth.createUser({ email, password, displayName, emailVerified: true })
    console.log(`  Created auth user: ${email} (${created.uid})`)
    return created.uid
  }
}

async function resolveUserIds() {
  if (!createAuthUsers) {
    const adminUid = process.env.SEED_ADMIN_UID
    const officer1Uid = process.env.SEED_OFFICER1_UID
    const officer2Uid = process.env.SEED_OFFICER2_UID
    if (!adminUid || !officer1Uid || !officer2Uid) {
      throw new Error(
        'Set SEED_CREATE_AUTH_USERS=false only when SEED_ADMIN_UID, SEED_OFFICER1_UID, and SEED_OFFICER2_UID are provided.',
      )
    }
    return { adminUid, officer1Uid, officer2Uid }
  }

  console.log('Ensuring demo Auth users...')
  const adminUid = await ensureAuthUser(users.admin)
  const officer1Uid = await ensureAuthUser(users.officer1)
  const officer2Uid = await ensureAuthUser(users.officer2)
  return { adminUid, officer1Uid, officer2Uid }
}

function buildDataset({ adminUid, officer1Uid, officer2Uid }) {
  const farmNorthPath = `users/${officer1Uid}/farms/farm-north-01`
  const farmSouthPath = `users/${officer1Uid}/farms/farm-south-02`
  const farmValleyPath = `users/${officer2Uid}/farms/farm-valley-03`

  return [
    // Access + profiles
    {
      path: `userAccess/${adminUid}`,
      data: {
        role: 'admin',
        status: 'active',
        assignedFarmIds: [],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: `userAccess/${officer1Uid}`,
      data: {
        role: 'field_officer',
        status: 'active',
        assignedFarmIds: [farmNorthPath, farmSouthPath],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: `userAccess/${officer2Uid}`,
      data: {
        role: 'field_officer',
        status: 'active',
        assignedFarmIds: [farmValleyPath],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: `staffProfiles/${adminUid}`,
      data: {
        displayName: users.admin.displayName,
        email: users.admin.email,
        phone: users.admin.phone,
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: `staffProfiles/${officer1Uid}`,
      data: {
        displayName: users.officer1.displayName,
        email: users.officer1.email,
        phone: users.officer1.phone,
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: `staffProfiles/${officer2Uid}`,
      data: {
        displayName: users.officer2.displayName,
        email: users.officer2.email,
        phone: users.officer2.phone,
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },

    // Suppliers
    {
      path: 'suppliers/sup-greenfield',
      data: {
        name: 'GreenField Agro Supplies',
        contactName: 'Ravi Silva',
        email: 'sales@greenfield.demo',
        phone: '+94112345678',
        address: '45 Kandy Road, Kurunegala',
        status: 'active',
        createdAt: ts('2026-02-15T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'suppliers/sup-cropcare',
      data: {
        name: 'CropCare Lanka',
        contactName: 'Anjali Wijesinghe',
        email: 'orders@cropcare.demo',
        phone: '+94119876543',
        address: '12 Agriculture Park, Anuradhapura',
        status: 'active',
        createdAt: ts('2026-02-15T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'suppliers/sup-pending-demo',
      data: {
        name: 'Island Seed Traders',
        contactName: 'Nimal Perera',
        email: 'hello@islandseeds.demo',
        phone: '+94115551234',
        address: 'Colombo Wholesale Market',
        status: 'pending',
        approvalNote: '',
        createdAt: ts('2026-07-20T00:00:00Z'),
        updatedAt: ts('2026-07-20T00:00:00Z'),
      },
    },

    // Supplier marketplace catalogue (distinct from company inventoryItems)
    {
      path: 'supplierProducts/sp-urea-greenfield',
      data: {
        supplierId: 'sup-greenfield',
        supplierName: 'GreenField Agro Supplies',
        name: 'Granular Urea 46%',
        category: 'Fertilizers',
        cropSuitability: ['Rice', 'Maize', 'Wheat'],
        description: 'High-nitrogen fertilizer pack for vegetative growth.',
        unit: 'bags',
        packSize: '50 kg',
        price: 6200,
        availabilityStatus: 'available',
        active: true,
        verified: true,
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'supplierProducts/sp-neem-cropcare',
      data: {
        supplierId: 'sup-cropcare',
        supplierName: 'CropCare Lanka',
        name: 'Neem Oil Concentrate',
        category: 'Chemicals',
        cropSuitability: ['Rice', 'Tomato', 'Maize'],
        description: 'Botanical pesticide suitable for early pest pressure.',
        unit: 'L',
        packSize: '1 L',
        price: 1850,
        availabilityStatus: 'available',
        active: true,
        verified: true,
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'supplierProducts/sp-rice-seed-cropcare',
      data: {
        supplierId: 'sup-cropcare',
        supplierName: 'CropCare Lanka',
        name: 'Certified Rice Seed BG 352',
        category: 'Seeds',
        cropSuitability: ['Rice'],
        description: 'Certified seed lot for irrigated rice systems.',
        unit: 'kg',
        packSize: '25 kg',
        price: 4200,
        availabilityStatus: 'limited',
        active: true,
        verified: true,
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },

    // Inventory
    {
      path: 'inventoryItems/inv-fert-urea',
      data: {
        name: 'Urea 46%',
        category: 'Fertilizers',
        sku: 'FERT-UREA-46',
        quantity: 42,
        reorderLevel: 10,
        unit: 'bags',
        supplierId: 'sup-greenfield',
        expiryDate: null,
        equipmentStatus: null,
        alternativeItemIds: ['inv-fert-npk'],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'inventoryItems/inv-fert-npk',
      data: {
        name: 'NPK 15-15-15',
        category: 'Fertilizers',
        sku: 'FERT-NPK-15',
        quantity: 18,
        reorderLevel: 8,
        unit: 'bags',
        supplierId: 'sup-greenfield',
        expiryDate: null,
        equipmentStatus: null,
        alternativeItemIds: ['inv-fert-urea'],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'inventoryItems/inv-chem-neem',
      data: {
        name: 'Neem-based bio pesticide',
        category: 'Chemicals',
        sku: 'CHEM-NEEM-BIO',
        quantity: 8,
        reorderLevel: 5,
        unit: 'liters',
        supplierId: 'sup-cropcare',
        expiryDate: ts('2026-12-31T00:00:00Z'),
        equipmentStatus: null,
        alternativeItemIds: [],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'inventoryItems/inv-seed-rice',
      data: {
        name: 'Hybrid Rice Seed BG 360',
        category: 'Seeds',
        sku: 'SEED-RICE-BG360',
        quantity: 0,
        reorderLevel: 10,
        unit: 'kg',
        supplierId: 'sup-cropcare',
        expiryDate: ts('2026-09-30T00:00:00Z'),
        equipmentStatus: null,
        alternativeItemIds: ['inv-seed-maize'],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'inventoryItems/inv-seed-maize',
      data: {
        name: 'Certified hybrid maize seed lot B',
        category: 'Seeds',
        sku: 'SEED-MAIZE-B',
        quantity: 25,
        reorderLevel: 6,
        unit: 'kg',
        supplierId: 'sup-cropcare',
        expiryDate: ts('2026-10-31T00:00:00Z'),
        equipmentStatus: null,
        alternativeItemIds: [],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'inventoryItems/inv-eq-sprayer',
      data: {
        name: 'Knapsack sprayer 16L',
        category: 'Equipment',
        sku: 'EQ-SPRAYER-16',
        quantity: 3,
        reorderLevel: 2,
        unit: 'units',
        supplierId: 'sup-greenfield',
        expiryDate: null,
        equipmentStatus: 'available',
        alternativeItemIds: [],
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },

    // Farms
    {
      path: farmNorthPath,
      data: {
        id: 'farm-north-01',
        farmName: 'North Paddy Block',
        farmerName: 'Sunil Bandara',
        cropType: 'Rice',
        locationText: 'Kurunegala',
        landSize: '2.5 acres',
        notes: 'Irrigated paddy field with good drainage.',
        latitude: 7.4863,
        longitude: 80.3647,
        plantingDate: Timestamp.fromMillis(1740787200000),
        assignedDeviceId: 'ESP32-FARM-001',
        assignedSensorDeviceId: 'ESP32-FARM-001',
        assignedCameraDeviceId: 'ESP32-CAM-001',
        remotePhotoUrl: null,
        createdAt: Timestamp.fromMillis(1740787200000),
        updatedAt: Timestamp.fromMillis(1752537600000),
      },
    },
    {
      path: farmSouthPath,
      data: {
        id: 'farm-south-02',
        farmName: 'South Vegetable Plot',
        farmerName: 'Malini Jayawardena',
        cropType: 'Tomato',
        locationText: 'Maho',
        landSize: '1.2 acres',
        notes: 'Drip irrigation installed last season.',
        latitude: 7.8221,
        longitude: 80.1456,
        plantingDate: Timestamp.fromMillis(1743465600000),
        assignedDeviceId: 'ESP32-FARM-002',
        assignedSensorDeviceId: 'ESP32-FARM-002',
        remotePhotoUrl: null,
        createdAt: Timestamp.fromMillis(1743465600000),
        updatedAt: Timestamp.fromMillis(1752537600000),
      },
    },
    {
      path: farmValleyPath,
      data: {
        id: 'farm-valley-03',
        farmName: 'Valley Maize Farm',
        farmerName: 'Ruwan Dissanayake',
        cropType: 'Maize',
        locationText: 'Anuradhapura',
        landSize: '4 acres',
        notes: 'Rain-fed maize with moderate pest pressure.',
        latitude: 8.3114,
        longitude: 80.4037,
        plantingDate: Timestamp.fromMillis(1746057600000),
        assignedDeviceId: 'ESP32-FARM-003',
        assignedSensorDeviceId: 'ESP32-FARM-003',
        remotePhotoUrl: null,
        createdAt: Timestamp.fromMillis(1746057600000),
        updatedAt: Timestamp.fromMillis(1752537600000),
      },
    },

    // IoT devices
    {
      path: 'iotDevices/device-001',
      data: {
        name: 'North Paddy Sensor Node',
        deviceId: 'ESP32-FARM-001',
        deviceType: 'sensor',
        farmPath: farmNorthPath,
        farmId: 'farm-north-01',
        officerUid: officer1Uid,
        status: 'online',
        lastSeen: ts('2026-07-15T10:30:00Z'),
        sensorTypes: ['soil_moisture', 'temperature', 'humidity', 'light', 'water_level'],
        firmwareVersion: '1.0.2',
        faultStatus: '',
        ingestKey: 'demo-key-esp32-farm-001',
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T10:30:00Z'),
      },
    },
    {
      path: 'iotDevices/device-002',
      data: {
        name: 'South Tomato Sensor Node',
        deviceId: 'ESP32-FARM-002',
        deviceType: 'sensor',
        farmPath: farmSouthPath,
        farmId: 'farm-south-02',
        officerUid: officer1Uid,
        status: 'offline',
        lastSeen: ts('2026-07-14T18:00:00Z'),
        sensorTypes: ['soil_moisture', 'temperature', 'humidity', 'light', 'water_level'],
        firmwareVersion: '1.0.1',
        faultStatus: 'No heartbeat for 16 hours',
        ingestKey: 'demo-key-esp32-farm-002',
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'iotDevices/device-003',
      data: {
        name: 'Valley Maize Sensor Node',
        deviceId: 'ESP32-FARM-003',
        deviceType: 'sensor',
        farmPath: farmValleyPath,
        farmId: 'farm-valley-03',
        officerUid: officer2Uid,
        status: 'maintenance',
        lastSeen: ts('2026-07-13T09:00:00Z'),
        sensorTypes: ['soil_moisture', 'temperature', 'humidity', 'light', 'water_level'],
        firmwareVersion: '1.0.0',
        faultStatus: 'Humidity sensor recalibration pending',
        ingestKey: 'demo-key-esp32-farm-003',
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T00:00:00Z'),
      },
    },
    {
      path: 'iotDevices/device-004',
      data: {
        name: 'North Paddy Field Camera',
        deviceId: 'ESP32-CAM-001',
        deviceType: 'camera',
        farmPath: farmNorthPath,
        farmId: 'farm-north-01',
        officerUid: officer1Uid,
        status: 'online',
        lastSeen: ts('2026-07-15T10:30:00Z'),
        sensorTypes: [],
        firmwareVersion: '1.0.0',
        faultStatus: '',
        ingestKey: 'demo-key-esp32-cam-001',
        createdAt: ts('2026-03-01T00:00:00Z'),
        updatedAt: ts('2026-07-15T10:30:00Z'),
      },
    },

    // Sensor readings
    {
      path: `users/${officer1Uid}/sensorReadings/reading-001`,
      data: {
        id: 'reading-001',
        farmId: 'farm-north-01',
        deviceId: 'ESP32-FARM-001',
        soilMoisturePercent: 28.5,
        temperatureCelsius: 31.2,
        humidityPercent: 72,
        lightIntensityLux: 45000,
        waterLevelPercent: 65,
        status: 'Warning',
        source: 'device',
        recordedAt: ms(1752537600000),
        updatedAt: ms(1752537600000),
      },
    },
    {
      path: `users/${officer1Uid}/sensorReadings/reading-002`,
      data: {
        id: 'reading-002',
        farmId: 'farm-south-02',
        deviceId: 'ESP32-FARM-002',
        soilMoisturePercent: 45,
        temperatureCelsius: 29.8,
        humidityPercent: 58,
        lightIntensityLux: 52000,
        waterLevelPercent: 80,
        status: 'Normal',
        source: 'simulated',
        recordedAt: ms(1752451200000),
        updatedAt: ms(1752451200000),
      },
    },

    // Visits + reports
    {
      path: `users/${officer1Uid}/farmVisits/visit-001`,
      data: {
        id: 'visit-001',
        farmId: 'farm-north-01',
        cropCondition: 'Good',
        notes: 'Tillering stage looks healthy. Minor weed pressure near canal edge.',
        remotePhotoUrl: null,
        latitude: 7.4863,
        longitude: 80.3647,
        createdAt: ms(1752451200000),
        updatedAt: ms(1752451200000),
      },
    },
    {
      path: `users/${officer1Uid}/reports/report-001`,
      data: {
        id: 'report-001',
        farmId: 'farm-south-02',
        cropType: 'Tomato',
        symptoms: 'Brown leaf spots spreading on lower canopy',
        severity: 'Medium',
        estimatedYield: '3.5 tons',
        notes: 'Likely early blight. Recommend fungicide review.',
        latitude: 7.8221,
        longitude: 80.1456,
        issueType: 'disease',
        detectedIssue: 'Tomato late blight',
        detectionConfidence: 78,
        recommendation: 'Apply recommended fungicide and improve airflow.',
        preventiveMeasures: 'Avoid overhead irrigation in evening.',
        createdAt: ms(1752364800000),
        updatedAt: ms(1752364800000),
      },
    },

    // Inventory requests
    {
      path: `users/${officer1Uid}/inventoryRequests/request-pending-01`,
      data: {
        id: 'request-pending-01',
        farmId: 'farm-north-01',
        itemType: 'Fertilizers',
        inventoryItemId: 'inv-fert-urea',
        itemName: 'Urea 46%',
        quantity: '5',
        reason: 'Top dressing needed before flowering stage.',
        status: 'Pending',
        availableStock: 42,
        alternativeItem: null,
        approvalNote: null,
        createdAt: ms(1752537600000),
        requestedAt: ms(1752537600000),
        updatedAt: ms(1752537600000),
      },
    },
    {
      path: `users/${officer1Uid}/inventoryRequests/request-approved-01`,
      data: {
        id: 'request-approved-01',
        farmId: 'farm-south-02',
        itemType: 'Chemicals',
        inventoryItemId: 'inv-chem-neem',
        itemName: 'Neem-based bio pesticide',
        quantity: '2',
        reason: 'Leaf spot control on tomato crop.',
        status: 'Approved',
        availableStock: 8,
        alternativeItem: null,
        approvalNote: 'Approved by administrator',
        reviewedAt: ms(1752451200000),
        approvedAt: ms(1752451200000),
        createdAt: ms(1752364800000),
        requestedAt: ms(1752364800000),
        updatedAt: ms(1752451200000),
      },
    },

    // Alerts + recommendations + transactions
    {
      path: 'alerts/alert-low-moisture',
      data: {
        title: 'Low soil moisture — North Paddy Block',
        message: 'Soil moisture dropped below 30%. Schedule irrigation within 24 hours.',
        severity: 'high',
        status: 'open',
        farmPath: farmNorthPath,
        deviceId: 'ESP32-FARM-001',
        source: 'sensor_threshold',
        createdAt: ts('2026-07-15T10:30:00Z'),
        updatedAt: ts('2026-07-15T10:30:00Z'),
      },
    },
    {
      path: 'alerts/alert-low-stock',
      data: {
        title: 'Low stock — Hybrid Rice Seed',
        message: 'Hybrid Rice Seed BG 360 is at 0 kg. Reorder immediately.',
        severity: 'medium',
        status: 'open',
        farmPath: null,
        deviceId: null,
        source: 'inventory',
        createdAt: ts('2026-07-15T08:00:00Z'),
        updatedAt: ts('2026-07-15T08:00:00Z'),
      },
    },
    {
      path: 'recommendations/rec-irrigation-01',
      data: {
        title: '1st nitrogen top dressing',
        message: 'Day 32 · Vegetative. Apply at active tillering. Recommended total: 80 kg. Suggested inventory: Urea.',
        priority: 'medium',
        type: 'FERTILIZER',
        source: 'calendar',
        activityId: 'rice-n-topdress-1',
        stage: 'VEGETATIVE',
        dayOfSeason: 32,
        suggestedQuantity: 80,
        quantityUnit: 'kg',
        activityStatus: 'due',
        farmPath: farmNorthPath,
        officerUid: officer1Uid,
        suggestedItemName: 'Urea',
        alternativeItemName: 'Potassium blend',
        createdAt: ts('2026-07-15T10:30:00Z'),
        updatedAt: ts('2026-07-15T10:30:00Z'),
      },
    },
    {
      path: 'stockTransactions/txn-001',
      data: {
        inventoryItemId: 'inv-chem-neem',
        inventoryRequestPath: `users/${officer1Uid}/inventoryRequests/request-approved-01`,
        officerUid: officer1Uid,
        type: 'request_approval',
        quantity: -2,
        balanceAfter: 8,
        note: 'Approved Neem-based bio pesticide',
        createdAt: ts('2026-07-14T12:00:00Z'),
      },
    },

    // Disease catalog
    {
      path: 'diseaseCatalog/rice-blast',
      data: {
        diseaseName: 'Rice blast',
        cropAffected: 'Rice',
        symptoms: 'Diamond-shaped lesions on leaves',
        treatment: 'Apply recommended fungicide at early tillering',
        prevention: 'Use resistant varieties and balanced nitrogen',
        severityGuidance: 'High severity if lesions spread to neck',
        updatedAt: ms(1752537600000),
      },
    },
  ]
}

async function writeBatches(documents) {
  const chunkSize = 400
  for (let index = 0; index < documents.length; index += chunkSize) {
    const batch = db.batch()
    const chunk = documents.slice(index, index + chunkSize)
    for (const { path, data } of chunk) {
      batch.set(db.doc(path), data, { merge: true })
    }
    await batch.commit()
    console.log(`  Wrote ${Math.min(index + chunk.length, documents.length)} / ${documents.length} documents`)
  }
}

async function main() {
  console.log(`Firestore project: ${projectId}`)
  if (shouldReset) {
    console.log('Mode: RESET + SEED (all Firestore data will be deleted first)\n')
    await wipeFirestore()
  } else {
    console.log('Mode: SEED (merge existing documents; use npm run seed:reset for a clean slate)\n')
  }

  const ids = await resolveUserIds()
  const documents = buildDataset(ids)
  console.log(`Writing ${documents.length} documents...`)
  await writeBatches(documents)

  console.log('\nDone. Demo logins:')
  console.log(`  Admin:    ${users.admin.email} / ${users.admin.password}`)
  console.log(`  Officer1: ${users.officer1.email} / ${users.officer1.password}`)
  console.log(`  Officer2: ${users.officer2.email} / ${users.officer2.password}`)
  console.log('\nUIDs:')
  console.log(`  ADMIN_UID=${ids.adminUid}`)
  console.log(`  OFFICER1_UID=${ids.officer1Uid}`)
  console.log(`  OFFICER2_UID=${ids.officer2Uid}`)
  console.log('\nESP32 ingest keys (set INGEST_KEY in firmware config.h):')
  console.log('  ESP32-FARM-001 → demo-key-esp32-farm-001')
  console.log('  ESP32-FARM-002 → demo-key-esp32-farm-002')
  console.log('  ESP32-FARM-003 → demo-key-esp32-farm-003')
  console.log('  ESP32-CAM-001  → demo-key-esp32-cam-001')
}

main().catch((error) => {
  console.error('Seed failed:', error.message ?? error)
  process.exit(1)
})
