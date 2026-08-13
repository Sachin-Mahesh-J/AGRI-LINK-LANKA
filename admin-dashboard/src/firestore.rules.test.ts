import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
  type RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Timestamp,
  collection,
  collectionGroup,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
} from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

const rulesPath = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../firestore.rules",
);
const hasEmulator = Boolean(process.env.FIRESTORE_EMULATOR_HOST);
let environment: RulesTestEnvironment;

const access = (role: "admin" | "field_officer", status = "active") => ({
  role,
  status,
  createdAt: Timestamp.now(),
  updatedAt: Timestamp.now(),
});

const farm = {
  id: "farm-1",
  farmName: "North Field",
  farmerName: "Asha",
  cropType: "Rice",
  locationText: "Pune",
  landSize: "2 acres",
  notes: "",
  createdAt: Timestamp.now(),
  updatedAt: Timestamp.now(),
};

const request = {
  id: "request-1",
  farmId: "farm-1",
  itemType: "Seeds",
  quantity: "5 kg",
  reason: "Replace damaged rows",
  status: "Pending",
  availableStock: 20,
  createdAt: Timestamp.now(),
  updatedAt: Timestamp.now(),
};

describe.skipIf(!hasEmulator)("Firestore RBAC rules", () => {
  beforeAll(async () => {
    environment = await initializeTestEnvironment({
      projectId: "demo-agriscout",
      firestore: { rules: fs.readFileSync(rulesPath, "utf8") },
    });
  });

  beforeEach(async () => {
    await environment.clearFirestore();
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "userAccess/admin-1"),
        access("admin"),
      );
      await setDoc(
        doc(context.firestore(), "userAccess/officer-1"),
        access("field_officer"),
      );
      await setDoc(
        doc(context.firestore(), "users/officer-1/farms/farm-1"),
        farm,
      );
      await setDoc(
        doc(context.firestore(), "users/officer-1/inventoryRequests/request-1"),
        request,
      );
    });
  });

  afterAll(async () => {
    await environment.cleanup();
  });

  it("denies unauthenticated reads", async () => {
    const db = environment.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(db, "users/officer-1/farms/farm-1")));
  });

  it("prevents an officer from self-promoting", async () => {
    const db = environment.authenticatedContext("new-officer").firestore();
    await assertFails(
      setDoc(doc(db, "userAccess/new-officer"), access("admin")),
    );
    await assertSucceeds(
      setDoc(
        doc(db, "userAccess/new-officer"),
        access("field_officer", "pending"),
      ),
    );
  });

  it("allows an active officer to read only owned farm data", async () => {
    const ownDb = environment.authenticatedContext("officer-1").firestore();
    const otherDb = environment.authenticatedContext("officer-2").firestore();
    await assertSucceeds(getDoc(doc(ownDb, "users/officer-1/farms/farm-1")));
    await assertFails(getDoc(doc(otherDb, "users/officer-1/farms/farm-1")));
  });

  it("allows administrators to query farms across officers", async () => {
    const db = environment.authenticatedContext("admin-1").firestore();
    await assertSucceeds(getDocs(collectionGroup(db, "farms")));
  });

  it("prevents officers from approving their own inventory requests", async () => {
    const db = environment.authenticatedContext("officer-1").firestore();
    await assertFails(
      updateDoc(doc(db, "users/officer-1/inventoryRequests/request-1"), {
        status: "Approved",
        reviewedAt: Timestamp.now(),
      }),
    );
  });

  it("allows administrators to approve an inventory request", async () => {
    const db = environment.authenticatedContext("admin-1").firestore();
    await assertSucceeds(
      updateDoc(doc(db, "users/officer-1/inventoryRequests/request-1"), {
        status: "Approved",
        reviewedAt: Timestamp.now(),
      }),
    );
  });

  it("denies inactive officers access to their operational data", async () => {
    await environment.withSecurityRulesDisabled(async (context) => {
      await updateDoc(doc(context.firestore(), "userAccess/officer-1"), {
        status: "inactive",
      });
    });
    const db = environment.authenticatedContext("officer-1").firestore();
    await assertFails(getDocs(collection(db, "users/officer-1/farms")));
  });

  it("allows active officers to read shared inventory stock", async () => {
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), "inventoryItems/item-1"), {
        name: "Urea",
        category: "Fertilizers",
        quantity: 12,
        reorderLevel: 5,
        unit: "bags",
        alternativeItemIds: [],
        createdAt: Timestamp.now(),
        updatedAt: Timestamp.now(),
      });
    });
    const db = environment.authenticatedContext("officer-1").firestore();
    await assertSucceeds(getDoc(doc(db, "inventoryItems/item-1")));
  });

  it("denies operational access when an ACL is absent or pending", async () => {
    const noAccess = environment
      .authenticatedContext("unknown-user")
      .firestore();
    await assertFails(
      getDocs(collection(noAccess, "users/unknown-user/farms")),
    );
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "userAccess/pending-user"),
        access("field_officer", "pending"),
      );
    });
    const pending = environment
      .authenticatedContext("pending-user")
      .firestore();
    await assertFails(getDocs(collection(pending, "inventoryItems")));
  });

  it("denies access when userAccess is deleted", async () => {
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(
        doc(context.firestore(), "userAccess/officer-1"),
        access("field_officer", "inactive"),
      );
    });
    const db = environment.authenticatedContext("officer-1").firestore();
    await assertFails(getDoc(doc(db, "users/officer-1/farms/farm-1")));
  });

  it("prevents officers from deleting reviewed inventory requests", async () => {
    await environment.withSecurityRulesDisabled(async (context) => {
      await updateDoc(
        doc(context.firestore(), "users/officer-1/inventoryRequests/request-1"),
        {
          status: "Approved",
          reviewedAt: Timestamp.now(),
        },
      );
    });
    const db = environment.authenticatedContext("officer-1").firestore();
    await assertFails(
      updateDoc(doc(db, "users/officer-1/inventoryRequests/request-1"), {
        quantity: "99 kg",
        updatedAt: Timestamp.now(),
      }),
    );
  });

  it("allows active officers to upsert their own recommendations", async () => {
    const now = Timestamp.now();
    const recommendation = {
      title: "Irrigation recommended",
      message: "Soil moisture is below threshold.",
      priority: "high",
      type: "IRRIGATION",
      farmPath: "users/officer-1/farms/farm-1",
      officerUid: "officer-1",
      suggestedItemName: "Urea",
      alternativeItemName: "Compost blend",
      createdAt: now,
      updatedAt: now,
    };
    const officerDb = environment.authenticatedContext("officer-1").firestore();
    const otherDb = environment.authenticatedContext("officer-2").firestore();
    await assertSucceeds(
      setDoc(
        doc(officerDb, "recommendations/officer-1_farm-1_irrigation"),
        recommendation,
      ),
    );
    await assertSucceeds(
      updateDoc(doc(officerDb, "recommendations/officer-1_farm-1_irrigation"), {
        message: "Updated irrigation guidance.",
        updatedAt: Timestamp.now(),
      }),
    );
    await assertFails(
      setDoc(doc(otherDb, "recommendations/officer-1_farm-1_irrigation"), {
        ...recommendation,
        officerUid: "officer-2",
        farmPath: "users/officer-2/farms/farm-1",
      }),
    );
  });
});
