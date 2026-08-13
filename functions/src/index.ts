import { initializeApp } from "firebase-admin/app";
import {
  onDocumentCreated,
  onDocumentUpdated,
} from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import * as logger from "firebase-functions/logger";
import { notifyInventoryRequestChange } from "./inventoryNotifications";
import {
  notifyHarvestRequestChange,
  notifyHarvestRequestCreated,
  notifyProductRequestChange,
} from "./marketplaceNotifications";
import { runLowStockCheck } from "./lowStockCheck";
import { ingestSensorReading } from "./iotIngest";
import { ingestCameraImage } from "./cameraIngest";
import { onCameraCaptureCreated } from "./cameraAiPipeline";

initializeApp();

export { ingestSensorReading, ingestCameraImage, onCameraCaptureCreated };

/**
 * Sends FCM alerts to field officers when an administrator reviews an
 * inventory request (approve, reject, or mark issued).
 */
export const onInventoryRequestReviewed = onDocumentUpdated(
  "users/{userId}/inventoryRequests/{requestId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();

    if (!after) {
      logger.warn("Inventory request update missing after snapshot", {
        requestId: event.params.requestId,
      });
      return;
    }

    try {
      await notifyInventoryRequestChange({
        userId: event.params.userId,
        requestId: event.params.requestId,
        requestPath: event.data!.after.ref.path,
        before,
        after,
      });
    } catch (error) {
      logger.error("Failed to notify inventory request change", {
        userId: event.params.userId,
        requestId: event.params.requestId,
        error,
      });
      throw error;
    }
  },
);

/**
 * Notifies field officers when a supplier/admin updates a product request.
 */
export const onProductRequestUpdated = onDocumentUpdated(
  "productRequests/{requestId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!after) {
      return;
    }
    try {
      await notifyProductRequestChange({
        requestId: event.params.requestId,
        requestPath: event.data!.after.ref.path,
        before,
        after,
      });
    } catch (error) {
      logger.error("Failed to notify product request change", {
        requestId: event.params.requestId,
        error,
      });
      throw error;
    }
  },
);

/**
 * Notifies field officers when a buyer expresses harvest interest.
 */
export const onHarvestRequestCreated = onDocumentCreated(
  "harvestRequests/{requestId}",
  async (event) => {
    const after = event.data?.data();
    if (!after) {
      return;
    }
    try {
      await notifyHarvestRequestCreated({
        requestId: event.params.requestId,
        requestPath: event.data!.ref.path,
        after,
      });
    } catch (error) {
      logger.error("Failed to notify harvest request create", {
        requestId: event.params.requestId,
        error,
      });
      throw error;
    }
  },
);

/**
 * Notifies officers/buyers when a harvest request status changes.
 */
export const onHarvestRequestUpdated = onDocumentUpdated(
  "harvestRequests/{requestId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!after) {
      return;
    }
    try {
      await notifyHarvestRequestChange({
        requestId: event.params.requestId,
        requestPath: event.data!.after.ref.path,
        before,
        after,
      });
    } catch (error) {
      logger.error("Failed to notify harvest request change", {
        requestId: event.params.requestId,
        error,
      });
      throw error;
    }
  },
);

/**
 * Daily scheduled check for inventory items at or below reorder level.
 * Creates or refreshes open alerts visible in the admin dashboard.
 */
export const checkLowStockDaily = onSchedule(
  {
    schedule: "every day 06:00",
    timeZone: "Asia/Colombo",
  },
  async () => {
    await runLowStockCheck();
  },
);
