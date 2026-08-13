import { createHash, randomBytes, timingSafeEqual } from "crypto";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";

export type SensorIngestPayload = {
  deviceId?: string;
  soilMoisturePercent?: number;
  temperatureCelsius?: number;
  humidityPercent?: number;
  lightIntensityLux?: number;
  waterLevelPercent?: number;
  status?: string;
  recordedAt?: number | string;
  batteryPercent?: number;
  signalStrength?: number;
  unit?: string;
};

export function hashIngestKey(key: string): string {
  return createHash("sha256").update(key, "utf8").digest("hex");
}

export function generateIngestKey(): string {
  return randomBytes(24).toString("base64url");
}

export function extractBearerToken(authorizationHeader?: string | null): string | null {
  if (!authorizationHeader) return null;
  const match = /^Bearer\s+(.+)$/i.exec(authorizationHeader.trim());
  return match?.[1]?.trim() || null;
}

export function safeEqualString(a: string, b: string): boolean {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  if (left.length !== right.length) return false;
  return timingSafeEqual(left, right);
}

export function classifyReading(
  soilMoisturePercent: number,
  temperatureCelsius: number,
  humidityPercent: number,
  waterLevelPercent: number,
): "Normal" | "Warning" | "Critical" {
  if (
    soilMoisturePercent < 18 ||
    temperatureCelsius > 40 ||
    waterLevelPercent < 12
  ) {
    return "Critical";
  }
  if (
    soilMoisturePercent < 32 ||
    temperatureCelsius > 35 ||
    humidityPercent < 35 ||
    waterLevelPercent < 25
  ) {
    return "Warning";
  }
  return "Normal";
}

export function parseRecordedAt(value: number | string | undefined, nowMs: number): number {
  if (value == null) return nowMs;
  let ms: number;
  if (typeof value === "number" && Number.isFinite(value)) {
    // Treat small values as seconds; large as millis. Reject ESP uptime (millis())
    // which lands in 1970 after *1000 and makes app readings look permanently stale.
    ms = value < 1_000_000_000_000 ? value * 1000 : value;
  } else {
    const parsed = Date.parse(String(value));
    ms = Number.isFinite(parsed) ? parsed : nowMs;
  }
  const minSaneMs = Date.parse("2020-01-01T00:00:00.000Z");
  const maxSkewMs = 24 * 60 * 60 * 1000;
  if (!Number.isFinite(ms) || ms < minSaneMs || ms > nowMs + maxSkewMs) {
    return nowMs;
  }
  return ms;
}

export function validateMetric(name: string, value: unknown): number {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    throw new Error(`Missing or invalid numeric field: ${name}`);
  }
  return value;
}

/**
 * Secure device ingest endpoint.
 *
 * Auth: Authorization: Bearer <ingestKey> (or X-Device-Key) + X-Device-Id / body.deviceId
 * Writes Admin SDK reading under the linked officer's sensorReadings path and
 * updates iotDevices lastSeen / lastReadingAt / status.
 */
export const ingestSensorReading = onRequest(
  {
    cors: true,
    invoker: "public",
  },
  async (req, res) => {
    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed. Use POST." });
      return;
    }

    try {
      const body = (req.body ?? {}) as SensorIngestPayload;
      const deviceId =
        String(req.get("x-device-id") || body.deviceId || "").trim();
      const providedKey =
        extractBearerToken(req.get("authorization")) ||
        String(req.get("x-device-key") || "").trim();

      if (!deviceId || !providedKey) {
        res.status(401).json({
          error: "Unauthorized. Provide X-Device-Id and Bearer ingest key.",
        });
        return;
      }

      const db = getFirestore();
      const snapshot = await db
        .collection("iotDevices")
        .where("deviceId", "==", deviceId)
        .limit(1)
        .get();

      if (snapshot.empty) {
        res.status(404).json({ error: "Unknown device." });
        return;
      }

      const deviceDoc = snapshot.docs[0];
      const device = deviceDoc.data();
      const storedKey = String(device.ingestKey || "").trim();
      if (!storedKey || !safeEqualString(storedKey, providedKey)) {
        res.status(401).json({ error: "Invalid device credentials." });
        return;
      }

      const status = String(device.status || "offline").toLowerCase();
      if (status === "inactive") {
        res.status(403).json({ error: "Device is inactive." });
        return;
      }

      const farmId = String(device.farmId || "").trim();
      const farmPath = String(device.farmPath || "").trim();
      const officerUid = String(device.officerUid || "").trim();
      if (!farmId || !farmPath || !officerUid) {
        res.status(409).json({
          error: "Device is not linked to a farm. Assign farmPath/farmId/officerUid first.",
        });
        return;
      }

      const soilMoisturePercent = validateMetric(
        "soilMoisturePercent",
        body.soilMoisturePercent,
      );
      const temperatureCelsius = validateMetric(
        "temperatureCelsius",
        body.temperatureCelsius,
      );
      const humidityPercent = validateMetric(
        "humidityPercent",
        body.humidityPercent,
      );
      const lightIntensityLux = validateMetric(
        "lightIntensityLux",
        body.lightIntensityLux,
      );
      const waterLevelPercent = validateMetric(
        "waterLevelPercent",
        body.waterLevelPercent,
      );

      const nowMs = Date.now();
      const recordedAtMs = parseRecordedAt(body.recordedAt, nowMs);
      const readingStatus =
        typeof body.status === "string" && body.status.trim()
          ? body.status.trim()
          : classifyReading(
              soilMoisturePercent,
              temperatureCelsius,
              humidityPercent,
              waterLevelPercent,
            );

      const readingRef = db
        .collection("users")
        .doc(officerUid)
        .collection("sensorReadings")
        .doc();

      const readingPayload = {
        id: readingRef.id,
        farmId,
        deviceId,
        soilMoisturePercent,
        temperatureCelsius,
        humidityPercent,
        lightIntensityLux,
        waterLevelPercent,
        status: readingStatus,
        source: "device",
        recordedAt: recordedAtMs,
        updatedAt: nowMs,
      };

      const deviceUpdate: Record<string, unknown> = {
        status: status === "maintenance" ? "maintenance" : "online",
        lastSeen: Timestamp.fromMillis(nowMs),
        lastReadingAt: Timestamp.fromMillis(recordedAtMs),
        updatedAt: Timestamp.fromMillis(nowMs),
      };
      if (typeof body.batteryPercent === "number") {
        deviceUpdate.batteryPercent = body.batteryPercent;
      }
      if (typeof body.signalStrength === "number") {
        deviceUpdate.signalStrength = body.signalStrength;
      }

      const batch = db.batch();
      batch.set(readingRef, readingPayload);
      batch.set(deviceDoc.ref, deviceUpdate, { merge: true });
      batch.set(
        db.doc(farmPath),
        {
          // Sensor node only — do not clear / overwrite camera assignment.
          assignedSensorDeviceId: deviceId,
          assignedDeviceId: deviceId, // Android / legacy field
          updatedAt: Timestamp.fromMillis(nowMs),
        },
        { merge: true },
      );
      await batch.commit();

      logger.info("Ingested device sensor reading", {
        deviceId,
        farmId,
        officerUid,
        readingId: readingRef.id,
        status: readingStatus,
      });

      res.status(201).json({
        ok: true,
        readingId: readingRef.id,
        farmId,
        deviceId,
        status: readingStatus,
        source: "device",
        recordedAt: recordedAtMs,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : "Ingest failed.";
      logger.error("Sensor ingest failed", { error: message });
      const statusCode = message.startsWith("Missing or invalid") ? 400 : 500;
      res.status(statusCode).json({ error: message });
    }
  },
);
