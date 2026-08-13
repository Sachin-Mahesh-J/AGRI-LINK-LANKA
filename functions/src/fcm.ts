import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import * as logger from "firebase-functions/logger";

export type AlertDataPayload = Record<string, string>;

export async function loadEnabledDeviceTokens(userId: string): Promise<string[]> {
  const snapshot = await getFirestore()
    .collection("users")
    .doc(userId)
    .collection("devices")
    .where("enabled", "==", true)
    .get();

  const tokens = snapshot.docs
    .map((doc) => {
      const token = doc.data().token;
      return typeof token === "string" ? token.trim() : "";
    })
    .filter((token) => token.length > 0);

  return [...new Set(tokens)];
}

export async function sendUserAlert(
  userId: string,
  title: string,
  message: string,
  data: AlertDataPayload,
): Promise<number> {
  const tokens = await loadEnabledDeviceTokens(userId);
  if (tokens.length === 0) {
    logger.info("No FCM tokens registered for user", { userId, title });
    return 0;
  }

  const response = await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title, body: message },
    data,
    android: { priority: "high" },
  });

  if (response.failureCount > 0) {
    logger.warn("Some FCM deliveries failed", {
      userId,
      successCount: response.successCount,
      failureCount: response.failureCount,
    });
  }

  return response.successCount;
}
