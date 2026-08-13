import {
  addDoc,
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  type DocumentData,
  type Query,
  type QueryDocumentSnapshot,
} from "firebase/firestore";
import { createUserWithEmailAndPassword, signOut } from "firebase/auth";
import { deleteObject, ref } from "firebase/storage";
import { db, provisioningAuth, storage } from "./firebase";
import type {
  Alert,
  Buyer,
  BuyerStatus,
  Farm,
  FarmVisit,
  FieldReport,
  HarvestListing,
  HarvestListingStatus,
  HarvestRequest,
  HarvestRequestStatus,
  InventoryItem,
  InventoryRequest,
  IoTDevice,
  CameraCapture,
  ProductRequest,
  ProductRequestStatus,
  Recommendation,
  SensorReading,
  StockTransaction,
  Supplier,
  SupplierProduct,
  SupplierStatus,
  UserAccess,
} from "./types";

const officerUidFromPath = (documentPath: string) => {
  const parts = documentPath.split("/");
  return parts[0] === "users" ? parts[1] : undefined;
};

const convert = <T extends { id: string }>(
  snap: QueryDocumentSnapshot<DocumentData>,
): T => {
  const path = snap.ref.path;
  return {
    id: snap.id,
    ...snap.data(),
    path,
    officerUid: officerUidFromPath(path),
  } as unknown as T;
};

const list = async <T extends { id: string }>(
  source: Query<DocumentData>,
): Promise<T[]> =>
  (await getDocs(source)).docs.map((snapshot) => convert<T>(snapshot));

const deleteCameraCapture = async (capture: CameraCapture) => {
  const officerUid =
    capture.officerUid ??
    (capture.path ? officerUidFromPath(capture.path) : undefined);
  if (!officerUid) {
    throw new Error("Missing officer UID for this capture.");
  }

  if (capture.storagePath) {
    try {
      await deleteObject(ref(storage, capture.storagePath));
    } catch (error) {
      const code = (error as { code?: string }).code;
      if (code !== "storage/object-not-found") {
        throw error;
      }
    }
  }

  const recommendationIds = new Set<string>();
  if (capture.pipelineRecommendationId) {
    recommendationIds.add(capture.pipelineRecommendationId);
  }
  const linkedRecommendations = await getDocs(
    query(
      collection(db, "recommendations"),
      where("activityId", "==", capture.id),
      limit(5),
    ),
  );
  for (const snapshot of linkedRecommendations.docs) {
    recommendationIds.add(snapshot.id);
  }
  await Promise.allSettled(
    [...recommendationIds].map((id) =>
      deleteDoc(doc(db, "recommendations", id)).then(() => undefined),
    ),
  );

  await deleteDoc(
    doc(db, "users", officerUid, "cameraCaptures", capture.id),
  );
};

export const repository = {
  getAccess: async (uid: string) => {
    const [accessSnapshot, profileSnapshot] = await Promise.all([
      getDoc(doc(db, "userAccess", uid)),
      getDoc(doc(db, "staffProfiles", uid)),
    ]);
    return accessSnapshot.exists()
      ? ({
          id: accessSnapshot.id,
          ...accessSnapshot.data(),
          ...(profileSnapshot.exists() ? profileSnapshot.data() : {}),
        } as UserAccess)
      : null;
  },
  users: async () => {
    const [access, profiles] = await Promise.all([
      list<UserAccess>(query(collection(db, "userAccess"), limit(500))),
      list<UserAccess>(query(collection(db, "staffProfiles"), limit(500))),
    ]);
    const profilesById = new Map(
      profiles.map((profile) => [profile.id, profile]),
    );
    return access.map((entry) => ({
      ...entry,
      assignedFarmIds: entry.assignedFarmIds?.map(
        (farmPath) => farmPath.split("/").at(-1) ?? farmPath,
      ),
      ...profilesById.get(entry.id),
      id: entry.id,
    }));
  },
  createOfficer: async (
    email: string,
    password: string,
    displayName: string,
    phone = "",
  ) => {
    const credential = await createUserWithEmailAndPassword(
      provisioningAuth,
      email,
      password,
    );
    const now = serverTimestamp();
    await Promise.all([
      setDoc(doc(db, "userAccess", credential.user.uid), {
        role: "field_officer",
        status: "active",
        assignedFarmIds: [],
        createdAt: now,
        updatedAt: now,
      }),
      setDoc(doc(db, "staffProfiles", credential.user.uid), {
        displayName,
        email,
        phone,
        createdAt: now,
        updatedAt: now,
      }),
    ]);
    await signOut(provisioningAuth);
  },
  farms: () => list<Farm>(query(collectionGroup(db, "farms"), limit(500))),
  reports: () =>
    list<FieldReport>(query(collectionGroup(db, "reports"), limit(500))),
  visits: () =>
    list<FarmVisit>(query(collectionGroup(db, "farmVisits"), limit(500))),
  inventory: () =>
    list<InventoryItem>(
      query(collection(db, "inventoryItems"), orderBy("name"), limit(500)),
    ),
  suppliers: () =>
    list<Supplier>(
      query(collection(db, "suppliers"), orderBy("name"), limit(500)),
    ),
  requests: () =>
    list<InventoryRequest>(
      query(collectionGroup(db, "inventoryRequests"), limit(500)),
    ),
  devices: () =>
    list<IoTDevice>(query(collection(db, "iotDevices"), limit(500))),
  readings: () =>
    list<SensorReading>(
      query(collectionGroup(db, "sensorReadings"), limit(1000)),
    ),
  cameraCaptures: () =>
    list<CameraCapture>(
      query(
        collectionGroup(db, "cameraCaptures"),
        orderBy("capturedAt", "desc"),
        limit(100),
      ),
    ),
  alerts: () => list<Alert>(query(collection(db, "alerts"), limit(500))),
  recommendations: () =>
    list<Recommendation>(query(collection(db, "recommendations"), limit(500))),
  transactions: () =>
    list<StockTransaction>(
      query(
        collection(db, "stockTransactions"),
        orderBy("createdAt", "desc"),
        limit(500),
      ),
    ),
  save: async (
    collectionName: "inventory" | "suppliers",
    id: string | undefined,
    value: DocumentData,
  ) => {
    const targetCollection =
      collectionName === "inventory" ? "inventoryItems" : "suppliers";
    const normalized =
      collectionName === "inventory"
        ? {
            name: value.name,
            category: value.category || "Other",
            sku: value.sku || "",
            quantity: Number(value.quantity || 0),
            reorderLevel: Number(value.reorderLevel || 0),
            unit: value.unit || "units",
            supplierId: value.supplierId || null,
            expiryDate: value.expiryDate || null,
            equipmentStatus: value.equipmentStatus || null,
            alternativeItemIds: value.alternativeItemIds || [],
          }
        : {
            name: value.name,
            contactName: value.contactName || "",
            email: value.email || "",
            phone: value.phone || "",
            address: value.address || "",
            status: value.status || "pending",
            uid: value.uid || null,
            approvalNote: value.approvalNote || "",
            verifiedAt: value.verifiedAt || null,
          };
    const payload = { ...normalized, updatedAt: serverTimestamp() };
    if (id) await updateDoc(doc(db, targetCollection, id), payload);
    else
      await addDoc(collection(db, targetCollection), {
        ...payload,
        createdAt: serverTimestamp(),
      });
  },
  remove: (collectionName: "inventory" | "suppliers", id: string) =>
    deleteDoc(
      doc(
        db,
        collectionName === "inventory" ? "inventoryItems" : "suppliers",
        id,
      ),
    ),
  saveDevice: async (id: string | undefined, value: DocumentData) => {
    const deviceRef = id
      ? doc(db, "iotDevices", id)
      : doc(collection(db, "iotDevices"));
    const deviceType = String(value.deviceType || "sensor").toLowerCase();
    const isCamera = deviceType === "camera";
    const payload = {
      name: value.name,
      deviceId: value.deviceId,
      deviceType,
      farmPath: value.farmPath || null,
      farmId: value.farmId || null,
      officerUid: value.officerUid || null,
      status: value.status || "offline",
      lastSeen: value.lastSeen || null,
      lastReadingAt: isCamera ? null : value.lastReadingAt || null,
      lastCaptureAt: isCamera ? value.lastCaptureAt || null : null,
      sensorTypes: isCamera
        ? []
        : value.sensorTypes || [
            "soil_moisture",
            "temperature",
            "humidity",
            "light",
            "water_level",
          ],
      firmwareVersion: value.firmwareVersion || "",
      faultStatus: value.faultStatus || "",
      ingestKey: value.ingestKey || null,
      batteryPercent:
        typeof value.batteryPercent === "number" ? value.batteryPercent : null,
      signalStrength:
        typeof value.signalStrength === "number" ? value.signalStrength : null,
      updatedAt: serverTimestamp(),
    };
    await runTransaction(db, async (transaction) => {
      transaction.set(
        deviceRef,
        id ? payload : { ...payload, createdAt: serverTimestamp() },
        { merge: true },
      );
      if (value.farmPath && value.deviceId) {
        // Keep sensor + camera as separate devices on the same farm.
        // Never overwrite the other role's assignment.
        const farmUpdate: DocumentData = {
          updatedAt: serverTimestamp(),
        };
        if (isCamera) {
          farmUpdate.assignedCameraDeviceId = value.deviceId;
        } else {
          farmUpdate.assignedSensorDeviceId = value.deviceId;
          farmUpdate.assignedDeviceId = value.deviceId; // Android / legacy
        }
        transaction.update(doc(db, value.farmPath), farmUpdate);
      }
    });
  },
  removeDevice: (id: string) => deleteDoc(doc(db, "iotDevices", id)),

  removeCameraCapture: deleteCameraCapture,

  removeAllCameraCaptures: async (options?: {
    farmId?: string;
    officerUid?: string;
  }) => {
    let officerUid = options?.officerUid;
    if (options?.farmId && !officerUid) {
      const farms = await list<Farm>(
        query(collectionGroup(db, "farms"), limit(500)),
      );
      officerUid = farms.find((farm) => farm.id === options.farmId)?.officerUid;
      if (!officerUid) {
        throw new Error(`Farm ${options.farmId} not found.`);
      }
    }

    let deleted = 0;
    while (true) {
      const batch = officerUid
        ? await list<CameraCapture>(
            query(
              collection(db, "users", officerUid, "cameraCaptures"),
              ...(options?.farmId
                ? [where("farmId", "==", options.farmId)]
                : []),
              limit(100),
            ),
          )
        : await list<CameraCapture>(
            query(collectionGroup(db, "cameraCaptures"), limit(100)),
          );
      if (!batch.length) break;
      for (const capture of batch) {
        await deleteCameraCapture(capture);
      }
      deleted += batch.length;
      if (batch.length < 100) break;
    }
    return deleted;
  },

  /**
   * Admin create/update of a farm under a field officer.
   * Officers can also create farms in the Android app; both appear here after sync.
   * IoT modules are optional at create time — link later via Configure Farm IoT.
   */
  saveFarmWithIoT: async (input: {
    officerUid: string;
    farmId?: string;
    farmName: string;
    farmerName: string;
    cropType: string;
    locationText: string;
    landSize?: string;
    notes?: string;
    sensorDeviceId?: string;
    sensorIngestKey?: string;
    cameraDeviceId?: string;
    cameraIngestKey?: string;
    linkIoT?: boolean;
  }) => {
    const officerUid = input.officerUid.trim();
    if (!officerUid) throw new Error("Select a field officer for this farm.");

    const sensorDeviceId = (input.sensorDeviceId || "").trim();
    const cameraDeviceId = (input.cameraDeviceId || "").trim();
    const sensorIngestKey = (input.sensorIngestKey || "").trim();
    const cameraIngestKey = (input.cameraIngestKey || "").trim();
    const linkIoT = Boolean(input.linkIoT);

    if (linkIoT) {
      if (!sensorDeviceId || !sensorIngestKey) {
        throw new Error("Sensor module Device ID and ingest key are required to link IoT.");
      }
      if (!cameraDeviceId || !cameraIngestKey) {
        throw new Error("Camera module Device ID and ingest key are required to link IoT.");
      }
      if (sensorDeviceId === cameraDeviceId) {
        throw new Error("Sensor and camera modules must use different Device IDs.");
      }
    }

    const farmId =
      input.farmId?.trim() ||
      `farm-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
    const farmPath = `users/${officerUid}/farms/${farmId}`;
    const farmRef = doc(db, farmPath);
    const existingFarm = await getDoc(farmRef);
    const now = serverTimestamp();

    const farmPayload: DocumentData = {
      id: farmId,
      farmName: input.farmName.trim(),
      farmerName: input.farmerName.trim(),
      cropType: input.cropType.trim(),
      locationText: input.locationText.trim(),
      landSize: input.landSize?.trim() || "",
      notes: input.notes?.trim() || "",
      updatedAt: now,
    };

    // Only touch device link fields when explicitly linking IoT (merge keeps prior links otherwise).
    if (linkIoT) {
      farmPayload.assignedDeviceId = sensorDeviceId;
      farmPayload.assignedSensorDeviceId = sensorDeviceId;
      farmPayload.assignedCameraDeviceId = cameraDeviceId;
    }

    await setDoc(
      farmRef,
      existingFarm.exists()
        ? farmPayload
        : { ...farmPayload, createdAt: now },
      { merge: true },
    );

    // Ensure the officer's access record includes this farm path when admin creates it.
    const accessRef = doc(db, "userAccess", officerUid);
    const accessSnap = await getDoc(accessRef);
    if (accessSnap.exists()) {
      const assigned = Array.isArray(accessSnap.data().assignedFarmIds)
        ? [...accessSnap.data().assignedFarmIds]
        : [];
      if (!assigned.includes(farmPath)) {
        assigned.push(farmPath);
        await updateDoc(accessRef, {
          assignedFarmIds: assigned,
          updatedAt: now,
        });
      }
    }

    if (linkIoT) {
      const upsertLinkedDevice = async (
        deviceType: "sensor" | "camera",
        deviceId: string,
        ingestKey: string,
      ) => {
        const existing = await getDocs(
          query(
            collection(db, "iotDevices"),
            where("deviceId", "==", deviceId),
            limit(1),
          ),
        );
        const deviceRef = existing.empty
          ? doc(collection(db, "iotDevices"))
          : existing.docs[0].ref;
        const isCamera = deviceType === "camera";
        const payload: DocumentData = {
          name: isCamera
            ? `${input.farmName.trim()} Camera`
            : `${input.farmName.trim()} Sensor`,
          deviceId,
          deviceType,
          farmPath,
          farmId,
          officerUid,
          status: existing.empty
            ? "offline"
            : existing.docs[0].data().status || "offline",
          sensorTypes: isCamera
            ? []
            : [
                "soil_moisture",
                "temperature",
                "humidity",
                "light",
                "water_level",
              ],
          firmwareVersion: existing.empty
            ? ""
            : existing.docs[0].data().firmwareVersion || "",
          faultStatus: "",
          ingestKey,
          updatedAt: now,
        };
        await setDoc(
          deviceRef,
          existing.empty ? { ...payload, createdAt: now } : payload,
          { merge: true },
        );
      };

      await upsertLinkedDevice("sensor", sensorDeviceId, sensorIngestKey);
      await upsertLinkedDevice("camera", cameraDeviceId, cameraIngestKey);
    }

    return {
      farmId,
      farmPath,
      officerUid,
      sensorDeviceId: linkIoT ? sensorDeviceId : undefined,
      cameraDeviceId: linkIoT ? cameraDeviceId : undefined,
    };
  },

  assignFarms: async (uid: string, farmIds: string[]) => {
    const farmDocuments = await getDocs(
      query(collectionGroup(db, "farms"), limit(1000)),
    );
    const farmPaths = farmDocuments.docs
      .filter((farmDocument) => farmIds.includes(farmDocument.id))
      .map((farmDocument) => farmDocument.ref.path);
    await updateDoc(doc(db, "userAccess", uid), {
      assignedFarmIds: farmPaths,
      updatedAt: serverTimestamp(),
    });
  },
  updateUserAccess: (
    uid: string,
    values: Pick<UserAccess, "role" | "status">,
  ) =>
    updateDoc(doc(db, "userAccess", uid), {
      ...values,
      updatedAt: serverTimestamp(),
    }),
  resolveAlert: (id: string) =>
    updateDoc(doc(db, "alerts", id), {
      status: "resolved",
      resolvedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }),
  rejectRequest: (
    requestPath: string,
    approvalNote = "Rejected by administrator",
  ) =>
    updateDoc(doc(db, requestPath), {
      status: "Rejected",
      approvalNote,
      reviewedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    }),
  issueRequest: async (requestPath: string) => {
    const requestRef = doc(db, requestPath);
    const snapshot = await getDoc(requestRef);
    if (!snapshot.exists()) throw new Error("Request no longer exists.");
    const request = snapshot.data() as InventoryRequest;
    if (!["Approved", "approved"].includes(String(request.status ?? ""))) {
      throw new Error("Only approved requests can be marked as issued.");
    }
    await updateDoc(requestRef, {
      status: "Issued",
      issuedAt: serverTimestamp(),
      issuedQuantity: request.quantity ?? null,
      updatedAt: serverTimestamp(),
    });
    await addDoc(collection(db, "stockTransactions"), {
      inventoryItemId: request.inventoryItemId ?? null,
      inventoryRequestPath: requestPath,
      officerUid: officerUidFromPath(requestPath) ?? null,
      type: "issue",
      quantity: 0,
      balanceAfter: request.availableStock ?? 0,
      note: `Issued ${request.itemName ?? request.itemType ?? "inventory item"}`,
      createdAt: serverTimestamp(),
    });
  },
  approveRequest: async (requestPath: string, inventoryItemId: string) => {
    const requestRef = doc(db, requestPath);
    const initialRequestSnapshot = await getDoc(requestRef);
    if (!initialRequestSnapshot.exists())
      throw new Error("Request no longer exists.");
    const initialRequest = initialRequestSnapshot.data() as InventoryRequest;
    if (!["Pending", "pending"].includes(String(initialRequest.status ?? ""))) {
      throw new Error("Request was already reviewed.");
    }
    if (!inventoryItemId)
      throw new Error("Select the inventory item to issue.");

    await runTransaction(db, async (transaction) => {
      const requestSnapshot = await transaction.get(requestRef);
      if (!requestSnapshot.exists())
        throw new Error("Request no longer exists.");
      const request = requestSnapshot.data() as InventoryRequest;
      if (!["Pending", "pending"].includes(String(request.status ?? ""))) {
        throw new Error("Request was already reviewed.");
      }

      const itemRef = doc(db, "inventoryItems", inventoryItemId);
      const itemSnapshot = await transaction.get(itemRef);
      if (!itemSnapshot.exists())
        throw new Error("Inventory item no longer exists.");
      if (
        request.itemType &&
        itemSnapshot.data().category !== request.itemType
      ) {
        throw new Error(
          "Selected inventory item does not match the requested category.",
        );
      }
      const available = Number(itemSnapshot.data().quantity ?? 0);
      const requested = Number.parseFloat(String(request.quantity ?? 0));
      if (requested <= 0 || available < requested)
        throw new Error("Insufficient stock for this request.");

      transaction.update(itemRef, {
        quantity: available - requested,
        updatedAt: serverTimestamp(),
      });
      transaction.update(requestRef, {
        inventoryItemId,
        itemName: itemSnapshot.data().name,
        status: "Approved",
        approvalNote: "Approved by administrator",
        reviewedAt: serverTimestamp(),
        approvedAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      transaction.set(doc(collection(db, "stockTransactions")), {
        inventoryItemId,
        inventoryRequestPath: requestPath,
        officerUid: officerUidFromPath(requestPath) ?? null,
        type: "request_approval",
        quantity: -requested,
        balanceAfter: available - requested,
        note: `Approved ${request.itemType || itemSnapshot.data().name}`,
        createdAt: serverTimestamp(),
      });
    });
  },
  supplierProducts: () =>
    list<SupplierProduct>(
      query(collection(db, "supplierProducts"), orderBy("name"), limit(500)),
    ),
  productRequests: () =>
    list<ProductRequest>(
      query(
        collection(db, "productRequests"),
        orderBy("createdAt", "desc"),
        limit(500),
      ),
    ),
  saveSupplierProduct: async (id: string | undefined, value: DocumentData) => {
    const payload = {
      supplierId: value.supplierId,
      supplierName: value.supplierName || "",
      name: value.name,
      category: value.category || "Other",
      cropSuitability: value.cropSuitability || [],
      description: value.description || "",
      unit: value.unit || "units",
      packSize: value.packSize || "",
      price:
        value.price === "" || value.price == null
          ? null
          : Number(value.price),
      availabilityStatus: value.availabilityStatus || "available",
      active: value.active !== false,
      verified: value.verified === true,
      updatedAt: serverTimestamp(),
    };
    if (id) await updateDoc(doc(db, "supplierProducts", id), payload);
    else
      await addDoc(collection(db, "supplierProducts"), {
        ...payload,
        createdAt: serverTimestamp(),
      });
  },
  removeSupplierProduct: (id: string) =>
    deleteDoc(doc(db, "supplierProducts", id)),
  reviewSupplier: async (
    supplierId: string,
    status: Extract<SupplierStatus, "active" | "rejected" | "inactive">,
    approvalNote = "",
  ) => {
    const payload: DocumentData = {
      status,
      approvalNote,
      updatedAt: serverTimestamp(),
    };
    if (status === "active") payload.verifiedAt = serverTimestamp();
    await updateDoc(doc(db, "suppliers", supplierId), payload);
  },
  updateProductRequestStatus: async (
    requestId: string,
    status: ProductRequestStatus,
    notes: { supplierNote?: string; adminNote?: string } = {},
  ) => {
    const payload: DocumentData = {
      status,
      updatedAt: serverTimestamp(),
    };
    if (notes.supplierNote !== undefined)
      payload.supplierNote = notes.supplierNote;
    if (notes.adminNote !== undefined) payload.adminNote = notes.adminNote;
    await updateDoc(doc(db, "productRequests", requestId), payload);
  },
  createSupplierAccount: async (
    email: string,
    password: string,
    supplierId: string,
    displayName: string,
    phone = "",
  ) => {
    const credential = await createUserWithEmailAndPassword(
      provisioningAuth,
      email,
      password,
    );
    const now = serverTimestamp();
    await Promise.all([
      setDoc(doc(db, "userAccess", credential.user.uid), {
        role: "supplier",
        status: "active",
        assignedFarmIds: [],
        supplierId,
        createdAt: now,
        updatedAt: now,
      }),
      setDoc(doc(db, "staffProfiles", credential.user.uid), {
        displayName,
        email,
        phone,
        createdAt: now,
        updatedAt: now,
      }),
      updateDoc(doc(db, "suppliers", supplierId), {
        uid: credential.user.uid,
        email,
        updatedAt: now,
      }),
    ]);
    await signOut(provisioningAuth);
  },
  buyers: () =>
    list<Buyer>(query(collection(db, "buyers"), orderBy("name"), limit(500))),
  getBuyer: async (buyerId: string) => {
    const snapshot = await getDoc(doc(db, "buyers", buyerId));
    return snapshot.exists()
      ? ({ id: snapshot.id, ...snapshot.data() } as Buyer)
      : null;
  },
  harvestListings: (options?: { buyerVisibleOnly?: boolean }) =>
    options?.buyerVisibleOnly
      ? list<HarvestListing>(
          query(
            collection(db, "harvestListings"),
            where("status", "==", "listed"),
            where("active", "==", true),
            where("verified", "==", true),
            where("visibility", "==", "public"),
            limit(500),
          ),
        )
      : list<HarvestListing>(
          query(
            collection(db, "harvestListings"),
            orderBy("updatedAt", "desc"),
            limit(500),
          ),
        ),
  harvestRequests: (options?: { buyerId?: string }) =>
    options?.buyerId
      ? list<HarvestRequest>(
          query(
            collection(db, "harvestRequests"),
            where("buyerId", "==", options.buyerId),
            orderBy("createdAt", "desc"),
            limit(500),
          ),
        )
      : list<HarvestRequest>(
          query(
            collection(db, "harvestRequests"),
            orderBy("createdAt", "desc"),
            limit(500),
          ),
        ),
  saveBuyer: async (id: string | undefined, value: DocumentData) => {
    const payload = {
      name: value.name,
      contactName: value.contactName || "",
      email: value.email || "",
      phone: value.phone || "",
      address: value.address || "",
      organizationType: value.organizationType || "other",
      status: value.status || "pending",
      approvalNote: value.approvalNote || "",
      uid: value.uid ?? null,
      updatedAt: serverTimestamp(),
    };
    if (id) await updateDoc(doc(db, "buyers", id), payload);
    else
      await addDoc(collection(db, "buyers"), {
        ...payload,
        createdAt: serverTimestamp(),
      });
  },
  reviewBuyer: async (
    buyerId: string,
    status: Extract<BuyerStatus, "active" | "rejected" | "inactive">,
    approvalNote = "",
  ) => {
    const payload: DocumentData = {
      status,
      approvalNote,
      updatedAt: serverTimestamp(),
    };
    if (status === "active") payload.verifiedAt = serverTimestamp();
    await updateDoc(doc(db, "buyers", buyerId), payload);
  },
  createBuyerAccount: async (
    email: string,
    password: string,
    buyerId: string,
    displayName: string,
    phone = "",
  ) => {
    const credential = await createUserWithEmailAndPassword(
      provisioningAuth,
      email,
      password,
    );
    const now = serverTimestamp();
    await Promise.all([
      setDoc(doc(db, "userAccess", credential.user.uid), {
        role: "buyer",
        status: "active",
        assignedFarmIds: [],
        buyerId,
        createdAt: now,
        updatedAt: now,
      }),
      setDoc(doc(db, "staffProfiles", credential.user.uid), {
        displayName,
        email,
        phone,
        createdAt: now,
        updatedAt: now,
      }),
      updateDoc(doc(db, "buyers", buyerId), {
        uid: credential.user.uid,
        email,
        updatedAt: now,
      }),
    ]);
    await signOut(provisioningAuth);
  },
  saveHarvestListing: async (id: string | undefined, value: DocumentData) => {
    const payload = {
      farmId: value.farmId || "",
      farmPath: value.farmPath || "",
      farmName: value.farmName || "",
      officerUid: value.officerUid || "",
      cropType: value.cropType || "",
      locationText: value.locationText || "",
      district: value.district || "",
      estimatedQuantityMin:
        value.estimatedQuantityMin === "" || value.estimatedQuantityMin == null
          ? null
          : Number(value.estimatedQuantityMin),
      estimatedQuantityMax:
        value.estimatedQuantityMax === "" || value.estimatedQuantityMax == null
          ? null
          : Number(value.estimatedQuantityMax),
      quantityUnit: value.quantityUnit || "tonnes",
      harvestWindowStartDay:
        value.harvestWindowStartDay === "" ||
        value.harvestWindowStartDay == null
          ? null
          : Number(value.harvestWindowStartDay),
      harvestWindowEndDay:
        value.harvestWindowEndDay === "" || value.harvestWindowEndDay == null
          ? null
          : Number(value.harvestWindowEndDay),
      harvestPeriodLabel: value.harvestPeriodLabel || "",
      qualityNote: value.qualityNote || "",
      confidence:
        value.confidence === "" || value.confidence == null
          ? null
          : Number(value.confidence),
      reliabilityLabel: value.reliabilityLabel || "",
      predictionSource: value.predictionSource || "heuristic",
      sourceRecommendationId: value.sourceRecommendationId || "",
      listingOrigin: value.listingOrigin || "prediction",
      status: (value.status as HarvestListingStatus) || "listed",
      visibility: value.visibility || "public",
      active: value.active !== false,
      verified: value.verified === true,
      adminNote: value.adminNote || "",
      updatedAt: serverTimestamp(),
    };
    if (id) await updateDoc(doc(db, "harvestListings", id), payload);
    else
      await addDoc(collection(db, "harvestListings"), {
        ...payload,
        createdAt: serverTimestamp(),
      });
  },
  publishHarvestListingFromRecommendation: async (
    recommendation: Recommendation,
    farm?: Farm | null,
  ) => {
    if ((recommendation.type || "").toUpperCase() !== "HARVEST") {
      throw new Error("Only HARVEST recommendations can become listings.");
    }
    const farmPath =
      recommendation.farmPath ||
      (farm?.officerUid && farm?.id
        ? `users/${farm.officerUid}/farms/${farm.id}`
        : "");
    const farmId =
      farm?.id ||
      farmPath.split("/").at(-1) ||
      recommendation.farmPath?.split("/").at(-1) ||
      "";
    const locationText = farm?.locationText || "";
    const district =
      locationText.split(",").map((part) => part.trim()).filter(Boolean).at(-1) ||
      locationText ||
      "";
    const listingId =
      recommendation.id ||
      `${farmId}_${recommendation.activityId || "harvest"}`;
    await setDoc(
      doc(db, "harvestListings", listingId),
      {
        farmId,
        farmPath,
        farmName: farm?.farmName || "",
        officerUid: recommendation.officerUid || farm?.officerUid || "",
        cropType: farm?.cropType || "",
        locationText,
        district,
        estimatedQuantityMin: null,
        estimatedQuantityMax: recommendation.suggestedQuantity ?? null,
        quantityUnit: recommendation.quantityUnit || "tonnes",
        harvestWindowStartDay: null,
        harvestWindowEndDay: null,
        harvestPeriodLabel: recommendation.activityStatus || "",
        qualityNote: recommendation.rationale || "",
        confidence: recommendation.confidence ?? null,
        reliabilityLabel:
          (recommendation.confidence ?? 0) >= 70
            ? "Moderate"
            : (recommendation.confidence ?? 0) >= 45
              ? "Limited"
              : "Low",
        predictionSource: recommendation.source || "heuristic",
        sourceRecommendationId: recommendation.id,
        listingOrigin: "prediction",
        status: "listed",
        visibility: "public",
        active: true,
        verified: true,
        adminNote: "",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      },
      { merge: true },
    );
    return listingId;
  },
  updateHarvestListingStatus: async (
    listingId: string,
    status: HarvestListingStatus,
    extras: { visibility?: string; adminNote?: string; active?: boolean } = {},
  ) => {
    const payload: DocumentData = {
      status,
      updatedAt: serverTimestamp(),
    };
    if (extras.visibility !== undefined) payload.visibility = extras.visibility;
    if (extras.adminNote !== undefined) payload.adminNote = extras.adminNote;
    if (extras.active !== undefined) payload.active = extras.active;
    if (status === "hidden") {
      payload.visibility = "hidden";
      payload.active = false;
    }
    await updateDoc(doc(db, "harvestListings", listingId), payload);
  },
  createHarvestRequest: async (value: DocumentData) => {
    await addDoc(collection(db, "harvestRequests"), {
      harvestListingId: value.harvestListingId,
      buyerId: value.buyerId,
      buyerUid: value.buyerUid || "",
      buyerName: value.buyerName || "",
      farmId: value.farmId || "",
      farmPath: value.farmPath || "",
      farmName: value.farmName || "",
      cropType: value.cropType || "",
      requestedQuantity: value.requestedQuantity ?? "",
      quantityUnit: value.quantityUnit || "tonnes",
      message: value.message || "",
      status: (value.status as HarvestRequestStatus) || "requested",
      buyerNote: value.buyerNote || "",
      adminNote: value.adminNote || "",
      officerNote: value.officerNote || "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  },
  updateHarvestRequestStatus: async (
    requestId: string,
    status: HarvestRequestStatus,
    notes: {
      buyerNote?: string;
      adminNote?: string;
      officerNote?: string;
    } = {},
  ) => {
    const payload: DocumentData = {
      status,
      updatedAt: serverTimestamp(),
    };
    if (notes.buyerNote !== undefined) payload.buyerNote = notes.buyerNote;
    if (notes.adminNote !== undefined) payload.adminNote = notes.adminNote;
    if (notes.officerNote !== undefined) payload.officerNote = notes.officerNote;
    await updateDoc(doc(db, "harvestRequests", requestId), payload);
  },
};
