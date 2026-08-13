import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as logger from "firebase-functions/logger";

/**
 * When a camera capture is ingested, seed the recommendation pipeline.
 * Full TFLite analysis runs on Android during sync; this creates a review
 * recommendation when a new field image arrives.
 */
export const onCameraCaptureCreated = onDocumentCreated(
  "users/{userId}/cameraCaptures/{captureId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const data = snapshot.data();
    const userId = event.params.userId;
    const captureId = event.params.captureId;
    const farmId = String(data.farmId || "").trim();
    const deviceId = String(data.deviceId || "").trim();

    if (!farmId) {
      logger.warn("Camera capture missing farmId", { captureId });
      return;
    }

    const db = getFirestore();
    let farmPath = "";
    let cropType = "Unknown";
    const farmSnap = await db
      .collection("users")
      .doc(userId)
      .collection("farms")
      .doc(farmId)
      .get();

    if (farmSnap.exists) {
      farmPath = farmSnap.ref.path;
      cropType = String(farmSnap.data()?.cropType || "Unknown");
    }

    const nowMs = Date.now();
    const recommendationRef = db.collection("recommendations").doc();
    await recommendationRef.set({
      title: "Review camera capture",
      message: `New field image from device ${deviceId || "camera"} on ${cropType} farm. AI analysis will run when the officer app syncs.`,
      priority: "medium",
      type: "RISK_ALERT",
      farmPath,
      officerUid: userId,
      source: "camera_capture",
      activityId: captureId,
      confidence: 50,
      issueSignal: "New camera capture pending AI review",
      agriculturalNeed: "Visual crop inspection",
      recommendedAction:
        "Review latest camera image and confirm disease detection results.",
      productCategory: "Pest Control",
      rationale: "Automated pipeline triggered by ESP32-CAM upload.",
      createdAt: Timestamp.fromMillis(nowMs),
      updatedAt: Timestamp.fromMillis(nowMs),
    });

    await snapshot.ref.set(
      {
        aiProcessed: false,
        pipelineRecommendationId: recommendationRef.id,
        updatedAt: nowMs,
      },
      { merge: true },
    );

    logger.info("Camera capture pipeline seeded", {
      captureId,
      userId,
      farmId,
      recommendationId: recommendationRef.id,
    });
  },
);
