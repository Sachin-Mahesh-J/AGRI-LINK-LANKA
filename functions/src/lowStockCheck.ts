import { FieldValue, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";

type InventoryItem = {
  name?: string;
  category?: string;
  quantity?: number;
  reorderLevel?: number;
  unit?: string;
};

export async function runLowStockCheck(): Promise<number> {
  const snapshot = await getFirestore().collection("inventoryItems").get();
  let alertsCreated = 0;

  for (const doc of snapshot.docs) {
    const item = doc.data() as InventoryItem;
    const quantity = Number(item.quantity ?? 0);
    const reorderLevel = Number(item.reorderLevel ?? 0);

    if (reorderLevel <= 0 || quantity > reorderLevel) {
      continue;
    }

    const name = item.name?.trim() || doc.id;
    const unit = item.unit?.trim() || "units";
    const title = "Low stock alert";
    const message = `${name} is below reorder level (${quantity} ${unit} remaining, reorder at ${reorderLevel}).`;
    const alertId = `low-stock-${doc.id}`;

    await getFirestore()
      .collection("alerts")
      .doc(alertId)
      .set(
        {
          title,
          message,
          severity: quantity <= 0 ? "critical" : "high",
          status: "open",
          source: "low_stock_check",
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true },
      );

    alertsCreated += 1;
  }

  logger.info("Low stock check completed", {
    itemsChecked: snapshot.size,
    alertsCreated,
  });

  return alertsCreated;
}
