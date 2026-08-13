import type { Timestamp } from "firebase/firestore";

export type AccessRole =
  | "admin"
  | "super_admin"
  | "field_officer"
  | "supplier"
  | "buyer";
export type DocDate = Timestamp | Date | string | number | null;
export type SupplierStatus = "pending" | "active" | "inactive" | "rejected";
export type BuyerStatus = "pending" | "active" | "inactive" | "rejected";
export type ProductAvailability = "available" | "limited" | "out_of_stock";
export type ProductRequestStatus =
  | "created"
  | "reviewed"
  | "accepted"
  | "rejected"
  | "preparing"
  | "dispatched"
  | "delivered"
  | "cancelled";
/** Harvest output listing lifecycle (distinct from supplier product requests). */
export type HarvestListingStatus =
  | "draft"
  | "listed"
  | "reserved"
  | "completed"
  | "cancelled"
  | "hidden";
/** Buyer interest / purchase request against a harvest listing (non-binding). */
export type HarvestRequestStatus =
  | "interested"
  | "requested"
  | "under_review"
  | "accepted"
  | "rejected"
  | "negotiated"
  | "reserved"
  | "completed"
  | "cancelled";
export type BuyerOrganizationType =
  | "wholesaler"
  | "supermarket"
  | "exporter"
  | "food_processor"
  | "juice_manufacturer"
  | "hotel"
  | "restaurant"
  | "other";
export type InventoryCategory =
  | "Fertilizers"
  | "Chemicals"
  | "Seeds"
  | "Equipment"
  | "Other"
  | "Irrigation";

export interface UserAccess {
  id: string;
  displayName?: string;
  email?: string;
  phone?: string;
  role?: AccessRole;
  status?: "pending" | "active" | "inactive";
  assignedFarmIds?: string[];
  /** Linked suppliers/{id} for supplier role accounts. */
  supplierId?: string;
  /** Linked buyers/{id} for buyer role accounts. */
  buyerId?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface Farm {
  id: string;
  path?: string;
  officerUid?: string;
  farmName?: string;
  farmerName?: string;
  cropType?: string;
  locationText?: string;
  landSize?: string;
  notes?: string;
  plantingDate?: DocDate;
  /** @deprecated Prefer assignedSensorDeviceId; kept for older docs / Android. */
  assignedDeviceId?: string;
  /** Sensor ESP32 linked to this farm (e.g. ESP32-FARM-001). */
  assignedSensorDeviceId?: string;
  /** Camera ESP32 linked to this farm (e.g. ESP32-CAM-001). */
  assignedCameraDeviceId?: string;
  photoLocalUri?: string;
  remotePhotoUrl?: string;
  latitude?: number;
  longitude?: number;
  gpsAccuracyMeters?: number;
  gpsCapturedAt?: DocDate;
  gpsSource?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface InventoryItem {
  id: string;
  name?: string;
  category?: string;
  sku?: string;
  quantity?: number;
  reorderLevel?: number;
  unit?: string;
  supplierId?: string;
  expiryDate?: DocDate;
  equipmentStatus?: string;
  alternativeItemIds?: string[];
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface Supplier {
  id: string;
  name?: string;
  contactName?: string;
  email?: string;
  phone?: string;
  address?: string;
  /** Linked Firebase Auth uid when the supplier can self-manage listings. */
  uid?: string | null;
  status?: SupplierStatus;
  approvalNote?: string;
  verifiedAt?: DocDate;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

/** Supplier-managed marketplace listing (distinct from company inventoryItems). */
export interface SupplierProduct {
  id: string;
  supplierId?: string;
  supplierName?: string;
  name?: string;
  category?: InventoryCategory | string;
  cropSuitability?: string[];
  description?: string;
  unit?: string;
  packSize?: string;
  price?: number | null;
  availabilityStatus?: ProductAvailability;
  active?: boolean;
  verified?: boolean;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

/** Officer → supplier product request (distinct from warehouse inventoryRequests). */
export interface ProductRequest {
  id: string;
  officerUid?: string;
  farmId?: string;
  farmPath?: string;
  recommendationId?: string;
  productCategory?: string;
  supplierProductId?: string;
  supplierId?: string;
  productName?: string;
  supplierName?: string;
  quantity?: number | string;
  unit?: string;
  issueSignal?: string;
  agriculturalNeed?: string;
  recommendedAction?: string;
  rationale?: string;
  status?: ProductRequestStatus;
  supplierNote?: string;
  adminNote?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

/** Commercial buyer organisation (harvest demand side). */
export interface Buyer {
  id: string;
  name?: string;
  contactName?: string;
  email?: string;
  phone?: string;
  address?: string;
  organizationType?: BuyerOrganizationType | string;
  /** Linked Firebase Auth uid when the buyer can self-manage requests. */
  uid?: string | null;
  status?: BuyerStatus;
  approvalNote?: string;
  verifiedAt?: DocDate;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

/**
 * Upcoming harvest opportunity derived from Phase 4 HARVEST recommendations.
 * Non-binding — not a confirmed sale.
 */
export interface HarvestListing {
  id: string;
  farmId?: string;
  farmPath?: string;
  farmName?: string;
  officerUid?: string;
  cropType?: string;
  locationText?: string;
  district?: string;
  estimatedQuantityMin?: number | null;
  estimatedQuantityMax?: number | null;
  quantityUnit?: string;
  harvestWindowStartDay?: number | null;
  harvestWindowEndDay?: number | null;
  harvestPeriodLabel?: string;
  qualityNote?: string;
  confidence?: number | null;
  reliabilityLabel?: string;
  predictionSource?: string;
  sourceRecommendationId?: string;
  listingOrigin?: "prediction" | "manual_confirm" | string;
  status?: HarvestListingStatus;
  visibility?: "public" | "hidden" | string;
  active?: boolean;
  verified?: boolean;
  adminNote?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

/** Buyer interest / purchase request against a harvest listing. */
export interface HarvestRequest {
  id: string;
  harvestListingId?: string;
  buyerId?: string;
  buyerUid?: string;
  buyerName?: string;
  farmId?: string;
  farmPath?: string;
  farmName?: string;
  cropType?: string;
  requestedQuantity?: number | string;
  quantityUnit?: string;
  message?: string;
  status?: HarvestRequestStatus;
  buyerNote?: string;
  adminNote?: string;
  officerNote?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface InventoryRequest {
  id: string;
  path?: string;
  officerUid?: string;
  farmId?: string;
  itemType?: string;
  inventoryItemId?: string;
  itemName?: string;
  quantity?: number | string;
  reason?: string;
  status?:
    | "Pending"
    | "Approved"
    | "Rejected"
    | "Issued"
    | "pending"
    | "approved"
    | "rejected"
    | "issued";
  availableStock?: number;
  alternativeItem?: string;
  approvalNote?: string;
  reviewedAt?: DocDate;
  createdAt?: DocDate;
  requestedAt?: DocDate;
  updatedAt?: DocDate;
}

export interface IoTDevice {
  id: string;
  name?: string;
  deviceId?: string;
  deviceType?: "sensor" | "camera" | string;
  farmPath?: string;
  farmId?: string;
  officerUid?: string;
  status?: "online" | "offline" | "maintenance" | "inactive" | "active";
  lastSeen?: DocDate;
  lastReadingAt?: DocDate;
  lastCaptureAt?: DocDate;
  sensorTypes?: string[];
  firmwareVersion?: string;
  faultStatus?: string;
  ingestKey?: string;
  batteryPercent?: number;
  signalStrength?: number;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface CameraCapture {
  id: string;
  path?: string;
  officerUid?: string;
  deviceId?: string;
  farmId?: string;
  storagePath?: string;
  imageUrl?: string;
  capturedAt?: DocDate;
  resolution?: string;
  fileSize?: number;
  aiProcessed?: boolean;
  diseaseDetected?: string | null;
  confidence?: number | null;
  detectedIssue?: string | null;
  source?: string;
  pipelineRecommendationId?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface SensorReading {
  id: string;
  path?: string;
  officerUid?: string;
  farmId?: string;
  deviceId?: string;
  temperatureCelsius?: number;
  humidityPercent?: number;
  soilMoisturePercent?: number;
  lightIntensityLux?: number;
  waterLevelPercent?: number;
  status?: string;
  source?: "simulated" | "device" | string;
  recordedAt?: DocDate;
  updatedAt?: DocDate;
}

export interface Alert {
  id: string;
  farmPath?: string;
  deviceId?: string;
  source?: string;
  title?: string;
  message?: string;
  severity?: "low" | "medium" | "high" | "critical";
  status?: "open" | "resolved";
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface Recommendation {
  id: string;
  farmPath?: string;
  officerUid?: string;
  type?: string;
  title?: string;
  message?: string;
  priority?: "low" | "medium" | "high" | "critical";
  suggestedItemName?: string;
  alternativeItemName?: string;
  source?: string;
  activityId?: string;
  stage?: string;
  dayOfSeason?: number;
  suggestedQuantity?: number;
  quantityUnit?: string;
  activityStatus?: string;
  confidence?: number;
  issueSignal?: string;
  agriculturalNeed?: string;
  recommendedAction?: string;
  productCategory?: string;
  rationale?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface StockTransaction {
  id: string;
  inventoryItemId?: string;
  inventoryRequestPath?: string;
  officerUid?: string;
  type?: "addition" | "removal" | "adjustment" | "request_approval" | "issue";
  quantity?: number;
  balanceAfter?: number;
  note?: string;
  createdAt?: DocDate;
}

export interface FieldReport {
  id: string;
  path?: string;
  officerUid?: string;
  farmId?: string;
  cropType?: string;
  symptoms?: string;
  severity?: string;
  estimatedYield?: string;
  pestObservations?: string;
  growthStage?: string;
  cropConditionDetail?: string;
  recommendedActions?: string;
  followUpNotes?: string;
  issueType?: string;
  detectedIssue?: string;
  detectionConfidence?: number;
  recommendation?: string;
  detectionExplanation?: string;
  detectionSource?: string;
  notes?: string;
  latitude?: number;
  longitude?: number;
  gpsAccuracyMeters?: number;
  gpsCapturedAt?: DocDate;
  gpsSource?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}

export interface FarmVisit {
  id: string;
  path?: string;
  officerUid?: string;
  farmId?: string;
  cropCondition?: string;
  cropConditionDetail?: string;
  pestObservations?: string;
  growthStage?: string;
  recommendedActions?: string;
  followUpNotes?: string;
  notes?: string;
  photoLocalUri?: string;
  remotePhotoUrl?: string;
  latitude?: number;
  longitude?: number;
  gpsAccuracyMeters?: number;
  gpsCapturedAt?: DocDate;
  gpsSource?: string;
  createdAt?: DocDate;
  updatedAt?: DocDate;
}
