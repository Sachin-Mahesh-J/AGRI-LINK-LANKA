import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { sendUserAlert } from "./fcm";

type InventoryRequestData = {
  status?: string;
  itemType?: string;
  itemName?: string;
  quantity?: string | number;
  approvalNote?: string;
  farmId?: string;
};

export function normalizeStatus(status: unknown): string {
  return String(status ?? "")
    .trim()
    .toLowerCase();
}

export function shouldNotifyInventoryTransition(
  before: InventoryRequestData | undefined,
  after: InventoryRequestData,
): boolean {
  const previous = normalizeStatus(before?.status);
  const next = normalizeStatus(after.status);

  if (!next || previous === next) {
    return false;
  }

  if (previous === "pending" && (next === "approved" || next === "rejected")) {
    return true;
  }

  if (previous === "approved" && next === "issued") {
    return true;
  }

  return false;
}

function itemLabel(request: InventoryRequestData): string {
  return (
    request.itemName?.trim() ||
    request.itemType?.trim() ||
    "inventory item"
  );
}

function quantityLabel(request: InventoryRequestData): string {
  const quantity = request.quantity;
  if (quantity === undefined || quantity === null || quantity === "") {
    return "";
  }
  return ` (${quantity})`;
}

export function buildInventoryNotification(
  status: string,
  request: InventoryRequestData,
): { title: string; message: string; severity: string } {
  const label = itemLabel(request);
  const qty = quantityLabel(request);
  const note = request.approvalNote?.trim();

  switch (normalizeStatus(status)) {
    case "approved":
      return {
        title: "Inventory request approved",
        message: `Your request for ${label}${qty} was approved.${
          note ? ` Note: ${note}` : ""
        }`,
        severity: "High",
      };
    case "rejected":
      return {
        title: "Inventory request rejected",
        message: `Your request for ${label}${qty} was rejected.${
          note ? ` Reason: ${note}` : ""
        }`,
        severity: "Medium",
      };
    case "issued":
      return {
        title: "Inventory issued",
        message: `${label}${qty} has been marked as issued and is ready for collection.`,
        severity: "High",
      };
    default:
      return {
        title: "Inventory request updated",
        message: `Your request for ${label}${qty} is now ${status}.`,
        severity: "Medium",
      };
  }
}

export async function notifyInventoryRequestChange(params: {
  userId: string;
  requestId: string;
  requestPath: string;
  before: InventoryRequestData | undefined;
  after: InventoryRequestData;
}): Promise<void> {
  const { userId, requestId, requestPath, before, after } = params;

  if (!shouldNotifyInventoryTransition(before, after)) {
    return;
  }

  const status = String(after.status ?? "");
  const { title, message, severity } = buildInventoryNotification(
    status,
    after,
  );
  const alertId = `inventory-${requestId}-${normalizeStatus(status)}`;

  await getFirestore()
    .collection("alerts")
    .doc(alertId)
    .set(
      {
        title,
        message,
        severity: severity.toLowerCase(),
        status: "open",
        source: "inventory_request",
        farmPath: after.farmId
          ? `users/${userId}/farms/${after.farmId}`
          : null,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );

  const delivered = await sendUserAlert(userId, title, message, {
    id: alertId,
    title,
    message,
    severity,
    affectedArea: "Inventory requests",
    type: "inventory_request",
    status,
    requestId,
    requestPath,
    actionRoute: "inventory",
  });

  logger.info("Inventory request notification sent", {
    userId,
    requestId,
    status,
    delivered,
  });
}
