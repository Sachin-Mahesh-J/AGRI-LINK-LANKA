import { timingSafeEqual } from "crypto";
import {
  DocumentReference,
  Firestore,
  Timestamp,
} from "firebase-admin/firestore";

export type AuthenticatedDevice = {
  ref: DocumentReference;
  id: string;
  deviceId: string;
  ingestKey: string;
  farmId: string;
  farmPath: string;
  officerUid: string;
  status: string;
  deviceType: string;
  data: FirebaseFirestore.DocumentData;
};

export function extractBearerToken(
  authorizationHeader?: string | null,
): string | null {
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

export async function authenticateDevice(
  db: Firestore,
  deviceId: string,
  providedKey: string,
): Promise<AuthenticatedDevice> {
  const snapshot = await db
    .collection("iotDevices")
    .where("deviceId", "==", deviceId)
    .limit(1)
    .get();

  if (snapshot.empty) {
    throw new Error("Unknown device.");
  }

  const deviceDoc = snapshot.docs[0];
  const device = deviceDoc.data();
  const storedKey = String(device.ingestKey || "").trim();
  if (!storedKey || !safeEqualString(storedKey, providedKey)) {
    throw new Error("Invalid device credentials.");
  }

  const status = String(device.status || "offline").toLowerCase();
  if (status === "inactive") {
    throw new Error("Device is inactive.");
  }

  const farmId = String(device.farmId || "").trim();
  const farmPath = String(device.farmPath || "").trim();
  const officerUid = String(device.officerUid || "").trim();
  if (!farmId || !farmPath || !officerUid) {
    throw new Error(
      "Device is not linked to a farm. Assign farmPath/farmId/officerUid first.",
    );
  }

  return {
    ref: deviceDoc.ref,
    id: deviceDoc.id,
    deviceId,
    ingestKey: storedKey,
    farmId,
    farmPath,
    officerUid,
    status,
    deviceType: String(device.deviceType || "sensor").toLowerCase(),
    data: device,
  };
}

export function deviceCredentialsFromRequest(req: {
  get: (name: string) => string | undefined;
  body?: unknown;
}): { deviceId: string; providedKey: string } {
  const body = (req.body ?? {}) as { deviceId?: string };
  const deviceId = String(req.get("x-device-id") || body.deviceId || "").trim();
  const providedKey =
    extractBearerToken(req.get("authorization")) ||
    String(req.get("x-device-key") || "").trim();
  return { deviceId, providedKey };
}

export function buildHealthUpdate(
  body: Record<string, unknown>,
  nowMs: number,
  currentStatus: string,
): Record<string, unknown> {
  const update: Record<string, unknown> = {
    status: currentStatus === "maintenance" ? "maintenance" : "online",
    lastSeen: Timestamp.fromMillis(nowMs),
    updatedAt: Timestamp.fromMillis(nowMs),
  };

  if (typeof body.firmwareVersion === "string" && body.firmwareVersion.trim()) {
    update.firmwareVersion = body.firmwareVersion.trim();
  }
  const rssi =
    typeof body.rssi === "number"
      ? body.rssi
      : typeof body.signalStrength === "number"
        ? body.signalStrength
        : null;
  if (typeof rssi === "number" && Number.isFinite(rssi)) {
    update.rssi = rssi;
    update.signalStrength = rssi;
  }
  if (typeof body.freeHeap === "number" && Number.isFinite(body.freeHeap)) {
    update.freeHeap = body.freeHeap;
  }
  if (
    typeof body.uptimeSeconds === "number" &&
    Number.isFinite(body.uptimeSeconds)
  ) {
    update.uptimeSeconds = body.uptimeSeconds;
  }
  if (
    typeof body.batteryPercent === "number" &&
    Number.isFinite(body.batteryPercent)
  ) {
    update.batteryPercent = body.batteryPercent;
  }

  return update;
}
