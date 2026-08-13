import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { sendUserAlert } from "./fcm";
import { normalizeStatus } from "./inventoryNotifications";

type ProductRequestData = {
  officerUid?: string;
  productName?: string;
  supplierName?: string;
  quantity?: string | number;
  unit?: string;
  status?: string;
  supplierNote?: string;
  adminNote?: string;
};

type HarvestRequestData = {
  farmPath?: string;
  farmName?: string;
  cropType?: string;
  buyerUid?: string;
  buyerName?: string;
  requestedQuantity?: string | number;
  quantityUnit?: string;
  status?: string;
  buyerNote?: string;
  adminNote?: string;
  officerNote?: string;
};

export function officerUidFromFarmPath(farmPath: unknown): string | null {
  const match = String(farmPath ?? "").match(/^users\/([^/]+)\/farms\/[^/]+$/);
  return match?.[1] ?? null;
}

export function shouldNotifyStatusChange(
  beforeStatus: unknown,
  afterStatus: unknown,
): boolean {
  const previous = normalizeStatus(beforeStatus);
  const next = normalizeStatus(afterStatus);
  return Boolean(next) && previous !== next;
}

function quantityLabel(
  quantity: string | number | undefined,
  unit?: string,
): string {
  if (quantity === undefined || quantity === null || quantity === "") {
    return "";
  }
  const unitSuffix = unit?.trim() ? ` ${unit.trim()}` : "";
  return ` (${quantity}${unitSuffix})`;
}

export function buildProductRequestNotification(
  status: string,
  request: ProductRequestData,
): { title: string; message: string; severity: string } {
  const product = request.productName?.trim() || "supplier product";
  const supplier = request.supplierName?.trim();
  const qty = quantityLabel(request.quantity, request.unit);
  const note =
    request.supplierNote?.trim() || request.adminNote?.trim() || "";
  const normalized = normalizeStatus(status);

  const baseMessage = supplier
    ? `${product}${qty} from ${supplier} is now ${normalized}.`
    : `${product}${qty} is now ${normalized}.`;

  switch (normalized) {
    case "accepted":
      return {
        title: "Supplier request accepted",
        message: note ? `${baseMessage} Note: ${note}` : baseMessage,
        severity: "High",
      };
    case "rejected":
      return {
        title: "Supplier request rejected",
        message: note ? `${baseMessage} Reason: ${note}` : baseMessage,
        severity: "Medium",
      };
    case "delivered":
      return {
        title: "Supplier order delivered",
        message: baseMessage,
        severity: "High",
      };
    case "cancelled":
      return {
        title: "Supplier request cancelled",
        message: baseMessage,
        severity: "Medium",
      };
    default:
      return {
        title: "Supplier request updated",
        message: note ? `${baseMessage} Note: ${note}` : baseMessage,
        severity: "Medium",
      };
  }
}

export function buildHarvestRequestOfficerNotification(
  status: string,
  request: HarvestRequestData,
  isCreate: boolean,
): { title: string; message: string; severity: string } {
  const buyer = request.buyerName?.trim() || "A buyer";
  const crop = request.cropType?.trim() || "harvest";
  const farm = request.farmName?.trim();
  const qty = quantityLabel(request.requestedQuantity, request.quantityUnit);
  const place = farm ? ` at ${farm}` : "";

  if (isCreate) {
    return {
      title: "New harvest buyer interest",
      message: `${buyer} requested ${crop}${qty}${place}.`,
      severity: "High",
    };
  }

  return {
    title: "Harvest request updated",
    message: `${buyer}'s request for ${crop}${qty}${place} is now ${normalizeStatus(status)}.`,
    severity: "Medium",
  };
}

export function buildHarvestRequestBuyerNotification(
  status: string,
  request: HarvestRequestData,
): { title: string; message: string; severity: string } {
  const crop = request.cropType?.trim() || "harvest";
  const farm = request.farmName?.trim();
  const qty = quantityLabel(request.requestedQuantity, request.quantityUnit);
  const place = farm ? ` at ${farm}` : "";
  const note = request.officerNote?.trim() || request.adminNote?.trim() || "";
  const normalized = normalizeStatus(status);

  switch (normalized) {
    case "accepted":
      return {
        title: "Harvest interest accepted",
        message: note
          ? `Your request for ${crop}${qty}${place} was accepted. Note: ${note}`
          : `Your request for ${crop}${qty}${place} was accepted.`,
        severity: "High",
      };
    case "rejected":
      return {
        title: "Harvest interest declined",
        message: note
          ? `Your request for ${crop}${qty}${place} was declined. Reason: ${note}`
          : `Your request for ${crop}${qty}${place} was declined.`,
        severity: "Medium",
      };
    default:
      return {
        title: "Harvest request updated",
        message: note
          ? `Your request for ${crop}${qty}${place} is now ${normalized}. Note: ${note}`
          : `Your request for ${crop}${qty}${place} is now ${normalized}.`,
        severity: "Medium",
      };
  }
}

export function shouldNotifyBuyerOfHarvestUpdate(
  before: HarvestRequestData | undefined,
  after: HarvestRequestData,
): boolean {
  if (!shouldNotifyStatusChange(before?.status, after.status)) {
    return false;
  }
  const next = normalizeStatus(after.status);
  return [
    "accepted",
    "rejected",
    "under_review",
    "negotiated",
    "reserved",
    "completed",
    "cancelled",
  ].includes(next);
}

async function writeAlert(params: {
  alertId: string;
  title: string;
  message: string;
  severity: string;
  source: string;
  farmPath?: string | null;
}): Promise<void> {
  await getFirestore()
    .collection("alerts")
    .doc(params.alertId)
    .set(
      {
        title: params.title,
        message: params.message,
        severity: params.severity.toLowerCase(),
        status: "open",
        source: params.source,
        farmPath: params.farmPath ?? null,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
}

export async function notifyProductRequestChange(params: {
  requestId: string;
  requestPath: string;
  before: ProductRequestData | undefined;
  after: ProductRequestData;
}): Promise<void> {
  const { requestId, requestPath, before, after } = params;
  const officerUid = after.officerUid?.trim();
  if (!officerUid) {
    return;
  }
  if (!shouldNotifyStatusChange(before?.status, after.status)) {
    return;
  }

  const status = String(after.status ?? "");
  const { title, message, severity } = buildProductRequestNotification(
    status,
    after,
  );
  const alertId = `product-request-${requestId}-${normalizeStatus(status)}`;

  await writeAlert({
    alertId,
    title,
    message,
    severity,
    source: "product_request",
    farmPath: null,
  });

  const delivered = await sendUserAlert(officerUid, title, message, {
    id: alertId,
    title,
    message,
    severity,
    affectedArea: "Supplier requests",
    type: "product_request",
    status,
    requestId,
    requestPath,
    actionRoute: "supplierRequests",
  });

  logger.info("Product request notification sent", {
    officerUid,
    requestId,
    status,
    delivered,
  });
}

export async function notifyHarvestRequestCreated(params: {
  requestId: string;
  requestPath: string;
  after: HarvestRequestData;
}): Promise<void> {
  const { requestId, requestPath, after } = params;
  const officerUid = officerUidFromFarmPath(after.farmPath);
  if (!officerUid) {
    return;
  }

  const status = String(after.status ?? "requested");
  const { title, message, severity } = buildHarvestRequestOfficerNotification(
    status,
    after,
    true,
  );
  const alertId = `harvest-request-${requestId}-created`;

  await writeAlert({
    alertId,
    title,
    message,
    severity,
    source: "harvest_request",
    farmPath: after.farmPath ?? null,
  });

  const delivered = await sendUserAlert(officerUid, title, message, {
    id: alertId,
    title,
    message,
    severity,
    affectedArea: "Harvest marketplace",
    type: "harvest_request",
    status,
    requestId,
    requestPath,
    actionRoute: "harvestListings",
  });

  logger.info("Harvest request created notification sent", {
    officerUid,
    requestId,
    delivered,
  });
}

export async function notifyHarvestRequestChange(params: {
  requestId: string;
  requestPath: string;
  before: HarvestRequestData | undefined;
  after: HarvestRequestData;
}): Promise<void> {
  const { requestId, requestPath, before, after } = params;
  if (!shouldNotifyStatusChange(before?.status, after.status)) {
    return;
  }

  const status = String(after.status ?? "");
  const normalized = normalizeStatus(status);
  const officerUid = officerUidFromFarmPath(after.farmPath);
  // Officer/admin accept/decline is meant for the buyer; skip pinging the officer again.
  const isOfficerFacingResponse = ["accepted", "rejected"].includes(normalized);

  if (officerUid && !isOfficerFacingResponse) {
    const { title, message, severity } = buildHarvestRequestOfficerNotification(
      status,
      after,
      false,
    );
    const alertId = `harvest-request-${requestId}-officer-${normalized}`;
    await writeAlert({
      alertId,
      title,
      message,
      severity,
      source: "harvest_request",
      farmPath: after.farmPath ?? null,
    });
    await sendUserAlert(officerUid, title, message, {
      id: alertId,
      title,
      message,
      severity,
      affectedArea: "Harvest marketplace",
      type: "harvest_request",
      status,
      requestId,
      requestPath,
      actionRoute: "harvestListings",
    });
  }

  const buyerUid = after.buyerUid?.trim();
  if (buyerUid && shouldNotifyBuyerOfHarvestUpdate(before, after)) {
    const { title, message, severity } = buildHarvestRequestBuyerNotification(
      status,
      after,
    );
    const alertId = `harvest-request-${requestId}-buyer-${normalizeStatus(status)}`;
    await writeAlert({
      alertId,
      title,
      message,
      severity,
      source: "harvest_request",
      farmPath: after.farmPath ?? null,
    });
    const delivered = await sendUserAlert(buyerUid, title, message, {
      id: alertId,
      title,
      message,
      severity,
      affectedArea: "Harvest marketplace",
      type: "harvest_request",
      status,
      requestId,
      requestPath,
      actionRoute: "harvest-marketplace",
    });
    logger.info("Harvest request buyer notification sent", {
      buyerUid,
      requestId,
      status,
      delivered,
    });
  }
}
