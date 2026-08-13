import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";
import { onRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import {
  authenticateDevice,
  buildHealthUpdate,
  deviceCredentialsFromRequest,
} from "./iotAuth";

export type CameraIngestPayload = {
  deviceId?: string;
  imageBase64?: string;
  width?: number;
  height?: number;
  capturedAt?: number | string;
  signalStrength?: number;
  firmwareVersion?: string;
};

function parseCapturedAt(
  value: number | string | undefined,
  nowMs: number,
): number {
  if (value == null) return nowMs;
  let ms: number;
  if (typeof value === "number" && Number.isFinite(value)) {
    // Treat small values as seconds; large as millis. Reject ESP uptime
    // (millis()) which would land in 1970 after *1000.
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

function decodeImagePayload(body: CameraIngestPayload): Buffer {
  const raw = String(body.imageBase64 || "").trim();
  if (!raw) {
    throw new Error("Missing imageBase64 payload.");
  }
  const base64 = raw.includes(",") ? raw.split(",").pop() || "" : raw;
  const buffer = Buffer.from(base64, "base64");
  if (buffer.length < 512) {
    throw new Error("Image payload too small.");
  }
  if (buffer.length > 512 * 1024) {
    throw new Error("Image payload exceeds 512 KB limit.");
  }
  return buffer;
}

/**
 * Secure camera image ingest: Storage upload + Firestore metadata.
 * Auth: same device ingest key as sensor readings.
 */
export const ingestCameraImage = onRequest(
  {
    cors: true,
    invoker: "public",
    maxInstances: 20,
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
      const body = (req.body ?? {}) as CameraIngestPayload;
      const { deviceId, providedKey } = deviceCredentialsFromRequest(req);
      if (!deviceId || !providedKey) {
        res.status(401).json({
          error: "Unauthorized. Provide X-Device-Id and Bearer ingest key.",
        });
        return;
      }

      const db = getFirestore();
      const device = await authenticateDevice(db, deviceId, providedKey);
      if (device.deviceType !== "camera") {
        res.status(403).json({
          error: "Device is not registered as a camera node.",
        });
        return;
      }

      const imageBuffer = decodeImagePayload(body);
      const nowMs = Date.now();
      const capturedAtMs = parseCapturedAt(body.capturedAt, nowMs);
      const width = typeof body.width === "number" ? body.width : null;
      const height = typeof body.height === "number" ? body.height : null;
      const resolution =
        width && height
          ? `${width}x${height}`
          : width || height
            ? `${width || "?"}x${height || "?"}`
            : null;

      const captureRef = db
        .collection("users")
        .doc(device.officerUid)
        .collection("cameraCaptures")
        .doc();
      const storagePath = `iot/captures/${device.officerUid}/${device.farmId}/${deviceId}/${captureRef.id}.jpg`;
      const bucket = getStorage().bucket();
      const file = bucket.file(storagePath);

      await file.save(imageBuffer, {
        contentType: "image/jpeg",
        resumable: false,
        metadata: {
          metadata: {
            deviceId,
            farmId: device.farmId,
            captureId: captureRef.id,
          },
        },
      });
      await file.makePublic();

      const imageUrl = `https://storage.googleapis.com/${bucket.name}/${storagePath}`;
      const capturePayload = {
        id: captureRef.id,
        deviceId,
        farmId: device.farmId,
        storagePath,
        imageUrl,
        capturedAt: capturedAtMs,
        resolution,
        fileSize: imageBuffer.length,
        aiProcessed: false,
        diseaseDetected: null,
        confidence: null,
        detectedIssue: null,
        source: "device",
        createdAt: nowMs,
        updatedAt: nowMs,
      };

      const deviceUpdate = buildHealthUpdate(
        body as Record<string, unknown>,
        nowMs,
        device.status,
      );
      deviceUpdate.lastCaptureAt = Timestamp.fromMillis(capturedAtMs);

      const batch = db.batch();
      batch.set(captureRef, capturePayload);
      batch.set(device.ref, deviceUpdate, { merge: true });
      // Link camera to farm without overwriting the sensor assignment.
      batch.set(
        db.doc(device.farmPath),
        {
          assignedCameraDeviceId: deviceId,
          updatedAt: Timestamp.fromMillis(nowMs),
        },
        { merge: true },
      );
      await batch.commit();

      logger.info("Ingested camera capture", {
        deviceId,
        farmId: device.farmId,
        captureId: captureRef.id,
        fileSize: imageBuffer.length,
      });

      res.status(201).json({
        ok: true,
        captureId: captureRef.id,
        storagePath,
        imageUrl,
        capturedAt: capturedAtMs,
      });
    } catch (error) {
      const message =
        error instanceof Error ? error.message : "Camera ingest failed.";
      logger.error("Camera ingest failed", { error: message });
      const statusCode =
        message === "Unknown device." || message === "Invalid device credentials."
          ? 401
          : message.startsWith("Missing") ||
              message.startsWith("Image payload") ||
              message.startsWith("Device is not")
            ? 400
            : 500;
      res.status(statusCode).json({ error: message });
    }
  },
);
