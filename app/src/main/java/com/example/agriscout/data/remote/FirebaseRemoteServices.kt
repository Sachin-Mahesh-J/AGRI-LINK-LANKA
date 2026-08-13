package com.example.agriscout.data.remote

import android.net.Uri
import com.example.agriscout.data.local.DiseaseCatalogEntity
import com.example.agriscout.data.local.FarmRecommendationEntity
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.FarmVisitEntity
import com.example.agriscout.data.local.FieldReportEntity
import com.example.agriscout.data.local.HarvestListingEntity
import com.example.agriscout.data.local.HarvestRequestEntity
import com.example.agriscout.data.local.InventoryItemEntity
import com.example.agriscout.data.local.InventoryRequestEntity
import com.example.agriscout.data.local.ProductRequestEntity
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.SupplierProductEntity
import com.example.agriscout.data.local.SyncStatus
import com.example.agriscout.data.local.WeatherWarningEntity
import com.example.agriscout.auth.FirebaseConfigurationException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.Locale

class CatalogRemoteService(private val firestore: FirebaseFirestore?) {
    suspend fun fetchDiseaseCatalog(): List<DiseaseCatalogEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("diseaseCatalog").get().await().documents.map { doc ->
            DiseaseCatalogEntity(
                id = doc.id,
                diseaseName = doc.getString("diseaseName").orEmpty(),
                cropAffected = doc.getString("cropAffected").orEmpty(),
                symptoms = doc.getString("symptoms").orEmpty(),
                treatment = doc.getString("treatment").orEmpty(),
                prevention = doc.getString("prevention").orEmpty(),
                severityGuidance = doc.getString("severityGuidance").orEmpty(),
                updatedAt = doc.getEpochMillis("updatedAt") ?: System.currentTimeMillis()
            )
        }
    }

    suspend fun fetchWeatherWarnings(): List<WeatherWarningEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("weatherWarnings").get().await().documents.map { doc ->
            WeatherWarningEntity(
                id = doc.id,
                title = doc.getString("title").orEmpty(),
                message = doc.getString("message").orEmpty(),
                affectedArea = doc.getString("affectedArea").orEmpty(),
                severity = doc.getString("severity").orEmpty(),
                validUntil = doc.getEpochMillis("validUntil") ?: 0L,
                updatedAt = doc.getEpochMillis("updatedAt") ?: System.currentTimeMillis()
            )
        }
    }

    suspend fun fetchInventoryItems(): List<InventoryItemEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("inventoryItems").get().await().documents.map { doc ->
            val alternativeIds = (doc.get("alternativeItemIds") as? List<*>)
                ?.mapNotNull { it as? String }
                ?.joinToString(",")
                .orEmpty()
            InventoryItemEntity(
                id = doc.id,
                name = doc.getString("name").orEmpty().ifBlank { doc.id },
                category = doc.getString("category").orEmpty().ifBlank { "Other" },
                quantity = doc.getLong("quantity")?.toInt() ?: 0,
                reorderLevel = doc.getLong("reorderLevel")?.toInt() ?: 0,
                unit = doc.getString("unit").orEmpty().ifBlank { "units" },
                alternativeItemIds = alternativeIds,
                updatedAt = doc.getEpochMillis("updatedAt") ?: System.currentTimeMillis()
            )
        }
    }

    suspend fun fetchSupplierProducts(): List<SupplierProductEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        // Query must match security rules: officers may only read active+verified products.
        // An unfiltered collection get() fails with permission-denied.
        return db.collection("supplierProducts")
            .whereEqualTo("active", true)
            .whereEqualTo("verified", true)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val cropSuitability = (doc.get("cropSuitability") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?.joinToString(",")
                    .orEmpty()
                SupplierProductEntity(
                    id = doc.id,
                    supplierId = doc.getString("supplierId").orEmpty(),
                    supplierName = doc.getString("supplierName").orEmpty(),
                    name = doc.getString("name").orEmpty().ifBlank { doc.id },
                    category = doc.getString("category").orEmpty().ifBlank { "Other" },
                    cropSuitability = cropSuitability,
                    description = doc.getString("description").orEmpty(),
                    unit = doc.getString("unit").orEmpty().ifBlank { "units" },
                    packSize = doc.getString("packSize").orEmpty(),
                    price = doc.getDouble("price") ?: doc.getLong("price")?.toDouble(),
                    availabilityStatus = doc.getString("availabilityStatus").orEmpty()
                        .ifBlank { "available" },
                    active = true,
                    verified = true,
                    updatedAt = doc.getEpochMillis("updatedAt") ?: System.currentTimeMillis()
                )
            }
    }

    suspend fun fetchHarvestListingsForOfficer(officerUid: String): List<HarvestListingEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("harvestListings")
            .whereEqualTo("officerUid", officerUid)
            .get()
            .await()
            .documents
            .map { doc -> doc.toHarvestListingEntity() }
    }

    suspend fun fetchHarvestRequestsForOfficer(officerUid: String): List<HarvestRequestEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        val prefix = "users/$officerUid/farms/"
        return db.collection("harvestRequests")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val farmPath = doc.getString("farmPath").orEmpty()
                if (!farmPath.startsWith(prefix)) return@mapNotNull null
                doc.toHarvestRequestEntity()
            }
    }
}

class SyncRemoteService(
    private val firestore: FirebaseFirestore?,
    private val storage: FirebaseStorage?
) {
    suspend fun uploadFarm(userId: String, farm: FarmEntity, remotePhotoUrl: String?): String {
        val db = firestore ?: throw FirebaseConfigurationException()
        val remoteDocId = farm.remoteId ?: farm.id
        val remoteRef = db.collection("users").document(userId).collection("farms").document(remoteDocId)
        remoteRef.set(farm.copy(remotePhotoUrl = remotePhotoUrl).toRemoteMap(), SetOptions.merge()).await()
        return remoteRef.id
    }

    suspend fun uploadReport(userId: String, report: FieldReportEntity, remoteImageUrl: String?): String {
        val db = firestore ?: throw FirebaseConfigurationException()
        val remoteDocId = report.remoteId ?: report.id
        val remoteRef = db.collection("users").document(userId).collection("reports").document(remoteDocId)
        remoteRef.set(report.copy(remoteImageUrl = remoteImageUrl).toRemoteMap(), SetOptions.merge()).await()
        return remoteRef.id
    }

    suspend fun uploadFarmVisit(userId: String, visit: FarmVisitEntity, remotePhotoUrl: String?): String {
        val db = firestore ?: throw FirebaseConfigurationException()
        val remoteDocId = visit.remoteId ?: visit.id
        val remoteRef = db.collection("users").document(userId).collection("farmVisits").document(remoteDocId)
        remoteRef.set(visit.copy(remotePhotoUrl = remotePhotoUrl).toRemoteMap(), SetOptions.merge()).await()
        return remoteRef.id
    }

    suspend fun uploadInventoryRequest(userId: String, request: InventoryRequestEntity): String {
        val db = firestore ?: throw FirebaseConfigurationException()
        val remoteDocId = request.remoteId ?: request.id
        val remoteRef = db.collection("users").document(userId).collection("inventoryRequests").document(remoteDocId)
        remoteRef.set(request.toRemoteMap(), SetOptions.merge()).await()
        return remoteRef.id
    }

    suspend fun uploadProductRequest(userId: String, request: ProductRequestEntity): String {
        val db = firestore ?: throw FirebaseConfigurationException()
        val remoteDocId = request.remoteId ?: request.id
        val farmPath = request.farmId?.let { "users/$userId/farms/$it" }
        db.collection("productRequests")
            .document(remoteDocId)
            .set(
                mapOf(
                    "officerUid" to userId,
                    "farmId" to request.farmId,
                    "farmPath" to farmPath,
                    "recommendationId" to request.recommendationId,
                    "productCategory" to request.productCategory,
                    "supplierProductId" to request.supplierProductId,
                    "supplierId" to request.supplierId,
                    "productName" to request.productName,
                    "supplierName" to request.supplierName,
                    "quantity" to request.quantity,
                    "unit" to request.unit,
                    "issueSignal" to request.issueSignal,
                    "agriculturalNeed" to request.agriculturalNeed,
                    "recommendedAction" to request.recommendedAction,
                    "rationale" to request.rationale,
                    "status" to request.status,
                    "supplierNote" to request.supplierNote,
                    "adminNote" to request.adminNote,
                    "createdAt" to request.createdAt,
                    "updatedAt" to request.updatedAt
                ),
                SetOptions.merge()
            )
            .await()
        return remoteDocId
    }

    suspend fun uploadHarvestListing(listing: HarvestListingEntity): String {
        val db = firestore ?: throw FirebaseConfigurationException()
        val remoteDocId = listing.remoteId ?: listing.id
        db.collection("harvestListings")
            .document(remoteDocId)
            .set(
                mapOf(
                    "farmId" to listing.farmId,
                    "farmPath" to listing.farmPath,
                    "farmName" to listing.farmName,
                    "officerUid" to listing.officerUid,
                    "cropType" to listing.cropType,
                    "locationText" to listing.locationText,
                    "district" to listing.district,
                    "estimatedQuantityMin" to listing.estimatedQuantityMin,
                    "estimatedQuantityMax" to listing.estimatedQuantityMax,
                    "quantityUnit" to listing.quantityUnit,
                    "harvestWindowStartDay" to listing.harvestWindowStartDay,
                    "harvestWindowEndDay" to listing.harvestWindowEndDay,
                    "harvestPeriodLabel" to listing.harvestPeriodLabel,
                    "qualityNote" to listing.qualityNote,
                    "confidence" to listing.confidence,
                    "reliabilityLabel" to listing.reliabilityLabel,
                    "predictionSource" to listing.predictionSource,
                    "sourceRecommendationId" to listing.sourceRecommendationId,
                    "listingOrigin" to listing.listingOrigin,
                    "status" to listing.status,
                    "visibility" to listing.visibility,
                    "active" to listing.active,
                    "verified" to listing.verified,
                    "adminNote" to listing.adminNote,
                    "createdAt" to listing.createdAt,
                    "updatedAt" to listing.updatedAt
                ),
                SetOptions.merge()
            )
            .await()
        return remoteDocId
    }

    suspend fun updateProductRequestStatus(
        requestId: String,
        status: String,
        updatedAt: Long
    ) {
        val db = firestore ?: throw FirebaseConfigurationException()
        db.collection("productRequests")
            .document(requestId)
            .update(
                mapOf(
                    "status" to status,
                    "updatedAt" to updatedAt
                )
            )
            .await()
    }

    suspend fun updateHarvestRequestResponse(
        requestId: String,
        status: String,
        officerNote: String?,
        updatedAt: Long
    ) {
        val db = firestore ?: throw FirebaseConfigurationException()
        db.collection("harvestRequests")
            .document(requestId)
            .update(
                mapOf(
                    "status" to status,
                    "officerNote" to (officerNote ?: ""),
                    "updatedAt" to updatedAt
                )
            )
            .await()
    }

    suspend fun fetchProductRequests(userId: String): List<ProductRequestEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("productRequests")
            .whereEqualTo("officerUid", userId)
            .get()
            .await()
            .documents
            .map { doc ->
                ProductRequestEntity(
                    id = doc.id,
                    userId = userId,
                    farmId = doc.getString("farmId"),
                    recommendationId = doc.getString("recommendationId"),
                    productCategory = doc.getString("productCategory").orEmpty(),
                    supplierProductId = doc.getString("supplierProductId").orEmpty(),
                    supplierId = doc.getString("supplierId").orEmpty(),
                    productName = doc.getString("productName").orEmpty(),
                    supplierName = doc.getString("supplierName").orEmpty(),
                    quantity = doc.get("quantity")?.toString().orEmpty(),
                    unit = doc.getString("unit").orEmpty().ifBlank { "units" },
                    issueSignal = doc.getString("issueSignal"),
                    agriculturalNeed = doc.getString("agriculturalNeed"),
                    recommendedAction = doc.getString("recommendedAction"),
                    rationale = doc.getString("rationale"),
                    status = doc.getString("status").orEmpty().ifBlank { "created" },
                    supplierNote = doc.getString("supplierNote"),
                    adminNote = doc.getString("adminNote"),
                    syncStatus = SyncStatus.SYNCED,
                    remoteId = doc.id,
                    createdAt = doc.getEpochMillis("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getEpochMillis("updatedAt") ?: System.currentTimeMillis()
                )
            }
    }

    suspend fun uploadSensorReading(userId: String, reading: SensorReadingEntity): String {
        val db = firestore ?: throw FirebaseConfigurationException()
        val remoteDocId = reading.remoteId ?: reading.id
        val remoteRef = db.collection("users").document(userId).collection("sensorReadings").document(remoteDocId)
        remoteRef.set(reading.toRemoteMap(), SetOptions.merge()).await()
        return remoteRef.id
    }

    suspend fun uploadRecommendation(userId: String, recommendation: FarmRecommendationEntity): String {
        val db = firestore ?: throw FirebaseConfigurationException()
        val remoteDocId = recommendation.remoteId ?: recommendation.id
        db.collection("recommendations")
            .document(remoteDocId)
            .set(recommendation.toRemoteMap(userId), SetOptions.merge())
            .await()
        return remoteDocId
    }

    suspend fun fetchRecommendations(userId: String): List<FarmRecommendationEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("recommendations")
            .whereEqualTo("officerUid", userId)
            .get()
            .await()
            .documents
            .mapNotNull { it.toFarmRecommendationEntity(userId) }
    }

    suspend fun fetchFarms(userId: String): List<FarmEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        val ownedFarms = db.collection("users")
            .document(userId)
            .collection("farms")
            .get()
            .await()
            .documents
            .map { it.toFarmEntity(userId) }
        val assignedPaths = db.collection("userAccess")
            .document(userId)
            .get()
            .await()
            .get("assignedFarmIds") as? List<*>
        val assignedFarms = assignedPaths
            .orEmpty()
            .mapNotNull { it as? String }
            .mapNotNull { path ->
                val ownerUserId = path.split('/').getOrNull(1) ?: return@mapNotNull null
                runCatching { db.document(path).get().await() }
                    .getOrNull()
                    ?.takeIf { it.exists() }
                    ?.toFarmEntity(
                        userId = userId,
                        ownerUserId = ownerUserId
                    )
            }
        return (ownedFarms + assignedFarms).distinctBy { it.id }
    }

    suspend fun fetchReports(userId: String): List<FieldReportEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("users")
            .document(userId)
            .collection("reports")
            .get()
            .await()
            .documents
            .map { it.toFieldReportEntity(userId) }
    }

    suspend fun fetchFarmVisits(userId: String): List<FarmVisitEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("users")
            .document(userId)
            .collection("farmVisits")
            .get()
            .await()
            .documents
            .map { it.toFarmVisitEntity(userId) }
    }

    suspend fun fetchInventoryRequests(userId: String): List<InventoryRequestEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("users")
            .document(userId)
            .collection("inventoryRequests")
            .get()
            .await()
            .documents
            .map { it.toInventoryRequestEntity(userId) }
    }

    suspend fun fetchSensorReadings(userId: String): List<SensorReadingEntity> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("users")
            .document(userId)
            .collection("sensorReadings")
            .get()
            .await()
            .documents
            .map { it.toSensorReadingEntity(userId) }
    }

    /** Linked ESP32 modules for this officer (sensor + camera). */
    suspend fun fetchOfficerIoTDevices(officerUid: String): List<FarmIoTModuleStatus> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("iotDevices")
            .whereEqualTo("officerUid", officerUid)
            .get()
            .await()
            .documents
            .map { doc ->
                FarmIoTModuleStatus(
                    deviceId = doc.getString("deviceId").orEmpty(),
                    deviceType = doc.getString("deviceType").orEmpty().ifBlank { "sensor" },
                    farmId = doc.getString("farmId"),
                    status = doc.getString("status").orEmpty().ifBlank { "offline" },
                    lastSeen = doc.getEpochMillis("lastSeen"),
                    lastReadingAt = doc.getEpochMillis("lastReadingAt"),
                    lastCaptureAt = doc.getEpochMillis("lastCaptureAt"),
                    signalStrength = doc.getLong("signalStrength")?.toInt()
                )
            }
    }

    suspend fun fetchCameraCapturesForFarm(
        userId: String,
        farmId: String,
        limit: Long = 12
    ): List<FarmCameraCapture> {
        val db = firestore ?: throw FirebaseConfigurationException()
        return db.collection("users")
            .document(userId)
            .collection("cameraCaptures")
            .whereEqualTo("farmId", farmId)
            .get()
            .await()
            .documents
            .map { doc ->
                FarmCameraCapture(
                    id = doc.getString("id").takeUnless { it.isNullOrBlank() } ?: doc.id,
                    deviceId = doc.getString("deviceId").orEmpty(),
                    farmId = doc.getString("farmId").orEmpty(),
                    imageUrl = doc.getString("imageUrl"),
                    capturedAt = doc.getEpochMillis("capturedAt") ?: 0L,
                    resolution = doc.getString("resolution")
                )
            }
            .sortedByDescending { it.capturedAt }
            .take(limit.toInt())
    }

    suspend fun uploadReportImageIfNeeded(userId: String, report: FieldReportEntity): String? {
        if (!report.remoteImageUrl.isNullOrBlank()) return report.remoteImageUrl
        val localUri = report.imageLocalUri ?: return null
        return uploadImage(userId, "reports", report.id, localUri)
    }

    suspend fun uploadFarmPhotoIfNeeded(userId: String, farm: FarmEntity): String? {
        if (!farm.remotePhotoUrl.isNullOrBlank() && farm.photoLocalUri.isNullOrBlank()) {
            return farm.remotePhotoUrl
        }
        val localUri = farm.photoLocalUri ?: return farm.remotePhotoUrl
        return uploadImage(userId, "farms", farm.id, localUri)
    }

    suspend fun uploadFarmVisitPhotoIfNeeded(userId: String, visit: FarmVisitEntity): String? {
        if (!visit.remotePhotoUrl.isNullOrBlank() && visit.photoLocalUri.isNullOrBlank()) {
            return visit.remotePhotoUrl
        }
        val localUri = visit.photoLocalUri ?: return visit.remotePhotoUrl
        return uploadImage(userId, "farmVisits", visit.id, localUri)
    }

    private suspend fun uploadImage(
        userId: String,
        folder: String,
        recordId: String,
        localUri: String
    ): String {
        val firebaseStorage = storage ?: throw FirebaseConfigurationException()
        val imageRef = firebaseStorage.reference.child("users/$userId/$folder/$recordId.jpg")
        imageRef.putFile(Uri.parse(localUri)).await()
        return imageRef.downloadUrl.await().toString()
    }

    suspend fun deleteFarmAndReports(userId: String, farmId: String) {
        val db = firestore ?: throw FirebaseConfigurationException()
        val reportDocs = db.collection("users")
            .document(userId)
            .collection("reports")
            .whereEqualTo("farmId", farmId)
            .get()
            .await()
            .documents

        reportDocs.forEach { reportDoc ->
            deleteReportImage(userId, reportDoc.id)
            reportDoc.reference.delete().await()
        }

        deleteFarmPhoto(userId, farmId)

        db.collection("users")
            .document(userId)
            .collection("farms")
            .document(farmId)
            .delete()
            .await()
    }

    suspend fun deleteReport(userId: String, reportId: String) {
        val db = firestore ?: throw FirebaseConfigurationException()
        deleteReportImage(userId, reportId)
        db.collection("users")
            .document(userId)
            .collection("reports")
            .document(reportId)
            .delete()
            .await()
    }

    private suspend fun deleteReportImage(userId: String, reportId: String) {
        deleteStorageImage(userId, "reports", reportId)
    }

    private suspend fun deleteFarmPhoto(userId: String, farmId: String) {
        deleteStorageImage(userId, "farms", farmId)
    }

    suspend fun deleteFarmVisitPhoto(userId: String, visitId: String) {
        deleteStorageImage(userId, "farmVisits", visitId)
    }

    private suspend fun deleteStorageImage(userId: String, folder: String, recordId: String) {
        val firebaseStorage = storage ?: return
        runCatching {
            firebaseStorage.reference.child("users/$userId/$folder/$recordId.jpg").delete().await()
        }
    }
}

private fun FarmEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "farmName" to farmName,
    "farmerName" to farmerName,
    "cropType" to cropType,
    "locationText" to locationText,
    "landSize" to landSize,
    "notes" to notes,
    "latitude" to latitude,
    "longitude" to longitude,
    "gpsAccuracyMeters" to gpsAccuracyMeters,
    "gpsCapturedAt" to gpsCapturedAt.toFirestoreTimestampOrNull(),
    "gpsSource" to gpsSource,
    "plantingDate" to plantingDate.toFirestoreTimestampOrNull(),
    "assignedDeviceId" to assignedDeviceId,
    "assignedSensorDeviceId" to assignedDeviceId,
    "assignedCameraDeviceId" to assignedCameraDeviceId,
    "remotePhotoUrl" to remotePhotoUrl,
    "createdAt" to createdAt.toFirestoreTimestamp(),
    "updatedAt" to updatedAt.toFirestoreTimestamp()
)

private fun FieldReportEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "farmId" to farmId,
    "cropType" to cropType,
    "symptoms" to symptoms,
    "severity" to severity,
    "estimatedYield" to estimatedYield,
    "notes" to notes,
    "pestObservations" to pestObservations,
    "growthStage" to growthStage,
    "cropConditionDetail" to cropConditionDetail,
    "recommendedActions" to recommendedActions,
    "followUpNotes" to followUpNotes,
    "latitude" to latitude,
    "longitude" to longitude,
    "gpsAccuracyMeters" to gpsAccuracyMeters,
    "gpsCapturedAt" to gpsCapturedAt.toFirestoreTimestampOrNull(),
    "gpsSource" to gpsSource,
    "remoteImageUrl" to remoteImageUrl,
    "issueType" to issueType,
    "detectedIssue" to detectedIssue,
    "detectionConfidence" to detectionConfidence,
    "matchedRuleId" to matchedRuleId,
    "recommendation" to recommendation,
    "preventiveMeasures" to preventiveMeasures,
    "detectionExplanation" to detectionExplanation,
    "detectionSource" to detectionSource,
    "detectionUpdatedAt" to detectionUpdatedAt.toFirestoreTimestampOrNull(),
    "createdAt" to createdAt.toFirestoreTimestamp(),
    "updatedAt" to updatedAt.toFirestoreTimestamp()
)

private fun FarmVisitEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "farmId" to farmId,
    "notes" to notes,
    "cropCondition" to cropCondition,
    "cropConditionDetail" to cropConditionDetail,
    "pestObservations" to pestObservations,
    "growthStage" to growthStage,
    "recommendedActions" to recommendedActions,
    "followUpNotes" to followUpNotes,
    "remotePhotoUrl" to remotePhotoUrl,
    "latitude" to latitude,
    "longitude" to longitude,
    "gpsAccuracyMeters" to gpsAccuracyMeters,
    "gpsCapturedAt" to gpsCapturedAt.toFirestoreTimestampOrNull(),
    "gpsSource" to gpsSource,
    "createdAt" to createdAt.toFirestoreTimestamp(),
    "updatedAt" to updatedAt.toFirestoreTimestamp()
)

private fun InventoryRequestEntity.toRemoteMap(): Map<String, Any?> {
    // Only include officer-writable create/update fields. Null review fields must not be
    // written — Firestore includes null keys in security-rule key checks and rejects them.
    val payload = mutableMapOf<String, Any?>(
        "id" to id,
        "farmId" to farmId,
        "itemType" to itemType,
        "quantity" to quantity,
        "reason" to reason,
        "status" to status,
        "availableStock" to availableStock,
        "alternativeItem" to alternativeItem,
        "createdAt" to createdAt.toFirestoreTimestamp(),
        "updatedAt" to updatedAt.toFirestoreTimestamp()
    )
    inventoryItemId?.takeIf { it.isNotBlank() }?.let { payload["inventoryItemId"] = it }
    itemName?.takeIf { it.isNotBlank() }?.let { payload["itemName"] = it }
    return payload
}

private fun FarmRecommendationEntity.toRemoteMap(userId: String): Map<String, Any?> = mapOf(
    "title" to title,
    "message" to message,
    "priority" to priority.lowercase(Locale.getDefault()),
    "type" to type,
    "farmPath" to "users/$userId/farms/$farmId",
    "officerUid" to userId,
    "suggestedItemName" to suggestedItemName.orEmpty(),
    "alternativeItemName" to alternativeItemName.orEmpty(),
    "source" to source,
    "activityId" to activityId,
    "stage" to stage,
    "dayOfSeason" to dayOfSeason,
    "suggestedQuantity" to suggestedQuantity,
    "quantityUnit" to quantityUnit,
    "activityStatus" to activityStatus,
    "confidence" to confidence,
    "issueSignal" to issueSignal,
    "agriculturalNeed" to agriculturalNeed,
    "recommendedAction" to recommendedAction,
    "productCategory" to productCategory,
    "rationale" to rationale,
    "createdAt" to createdAt.toFirestoreTimestamp(),
    "updatedAt" to updatedAt.toFirestoreTimestamp()
)

private fun SensorReadingEntity.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "farmId" to farmId,
    "deviceId" to deviceId,
    "soilMoisturePercent" to soilMoisturePercent,
    "temperatureCelsius" to temperatureCelsius,
    "humidityPercent" to humidityPercent,
    "lightIntensityLux" to lightIntensityLux,
    "waterLevelPercent" to waterLevelPercent,
    "status" to status,
    "source" to source,
    // Keep millis to match Cloud Functions ingestSensorReading writes.
    "recordedAt" to recordedAt,
    "updatedAt" to updatedAt.toFirestoreTimestamp()
)

private fun DocumentSnapshot.toFarmEntity(
    userId: String,
    ownerUserId: String = userId
): FarmEntity = FarmEntity(
    id = getString("id").takeUnless { it.isNullOrBlank() } ?: id,
    userId = userId,
    ownerUserId = ownerUserId,
    farmName = getString("farmName").orEmpty(),
    farmerName = getString("farmerName").orEmpty(),
    cropType = getString("cropType").orEmpty(),
    locationText = getString("locationText").orEmpty(),
    landSize = getString("landSize").orEmpty(),
    notes = getString("notes").orEmpty(),
    latitude = getDouble("latitude"),
    longitude = getDouble("longitude"),
    gpsAccuracyMeters = getDouble("gpsAccuracyMeters"),
    gpsCapturedAt = getEpochMillis("gpsCapturedAt"),
    gpsSource = getString("gpsSource"),
    plantingDate = getEpochMillis("plantingDate"),
    assignedDeviceId = getString("assignedSensorDeviceId")
        ?: getString("assignedDeviceId"),
    assignedCameraDeviceId = getString("assignedCameraDeviceId"),
    photoLocalUri = null,
    remotePhotoUrl = getString("remotePhotoUrl") ?: getString("photoLocalUri")?.takeIf { it.startsWith("http") },
    syncStatus = SyncStatus.SYNCED,
    remoteId = id,
    createdAt = getEpochMillis("createdAt") ?: System.currentTimeMillis(),
    updatedAt = getEpochMillis("updatedAt") ?: System.currentTimeMillis()
)

private fun DocumentSnapshot.toFieldReportEntity(userId: String): FieldReportEntity = FieldReportEntity(
    id = getString("id").takeUnless { it.isNullOrBlank() } ?: id,
    farmId = getString("farmId").orEmpty(),
    userId = userId,
    cropType = getString("cropType").orEmpty(),
    symptoms = getString("symptoms").orEmpty(),
    severity = getString("severity").orEmpty(),
    estimatedYield = getString("estimatedYield").orEmpty(),
    notes = getString("notes").orEmpty(),
    pestObservations = getString("pestObservations").orEmpty(),
    growthStage = getString("growthStage").orEmpty(),
    cropConditionDetail = getString("cropConditionDetail").orEmpty(),
    recommendedActions = getString("recommendedActions").orEmpty(),
    followUpNotes = getString("followUpNotes").orEmpty(),
    latitude = getDouble("latitude"),
    longitude = getDouble("longitude"),
    gpsAccuracyMeters = getDouble("gpsAccuracyMeters"),
    gpsCapturedAt = getEpochMillis("gpsCapturedAt"),
    gpsSource = getString("gpsSource"),
    imageLocalUri = null,
    remoteImageUrl = getString("remoteImageUrl"),
    issueType = getString("issueType"),
    detectedIssue = getString("detectedIssue"),
    detectionConfidence = getLong("detectionConfidence")?.toInt(),
    matchedRuleId = getString("matchedRuleId"),
    recommendation = getString("recommendation"),
    preventiveMeasures = getString("preventiveMeasures"),
    detectionExplanation = getString("detectionExplanation"),
    detectionSource = getString("detectionSource"),
    detectionUpdatedAt = getEpochMillis("detectionUpdatedAt"),
    syncStatus = SyncStatus.SYNCED,
    remoteId = id,
    createdAt = getEpochMillis("createdAt") ?: System.currentTimeMillis(),
    updatedAt = getEpochMillis("updatedAt") ?: System.currentTimeMillis()
)

private fun DocumentSnapshot.toFarmVisitEntity(userId: String): FarmVisitEntity = FarmVisitEntity(
    id = getString("id").takeUnless { it.isNullOrBlank() } ?: id,
    farmId = getString("farmId").orEmpty(),
    userId = userId,
    notes = getString("notes").orEmpty(),
    cropCondition = getString("cropCondition").orEmpty(),
    cropConditionDetail = getString("cropConditionDetail").orEmpty(),
    pestObservations = getString("pestObservations").orEmpty(),
    growthStage = getString("growthStage").orEmpty(),
    recommendedActions = getString("recommendedActions").orEmpty(),
    followUpNotes = getString("followUpNotes").orEmpty(),
    photoLocalUri = null,
    remotePhotoUrl = getString("remotePhotoUrl") ?: getString("photoLocalUri")?.takeIf { it.startsWith("http") },
    latitude = getDouble("latitude"),
    longitude = getDouble("longitude"),
    gpsAccuracyMeters = getDouble("gpsAccuracyMeters"),
    gpsCapturedAt = getEpochMillis("gpsCapturedAt"),
    gpsSource = getString("gpsSource"),
    syncStatus = SyncStatus.SYNCED,
    remoteId = id,
    createdAt = getEpochMillis("createdAt") ?: System.currentTimeMillis(),
    updatedAt = getEpochMillis("updatedAt") ?: System.currentTimeMillis()
)

private fun DocumentSnapshot.toInventoryRequestEntity(userId: String): InventoryRequestEntity = InventoryRequestEntity(
    id = getString("id").takeUnless { it.isNullOrBlank() } ?: id,
    userId = userId,
    farmId = getString("farmId"),
    itemType = getString("itemType").orEmpty(),
    quantity = get("quantity")?.toString().orEmpty(),
    reason = getString("reason").orEmpty(),
    status = getString("status").orEmpty(),
    availableStock = getLong("availableStock")?.toInt() ?: 0,
    alternativeItem = getString("alternativeItem"),
    inventoryItemId = getString("inventoryItemId"),
    itemName = getString("itemName"),
    approvalNote = getString("approvalNote"),
    reviewedAt = getEpochMillis("reviewedAt"),
    approvedAt = getEpochMillis("approvedAt"),
    issuedAt = getEpochMillis("issuedAt"),
    issuedQuantity = getDouble("issuedQuantity"),
    syncStatus = SyncStatus.SYNCED,
    remoteId = id,
    createdAt = getEpochMillis("createdAt") ?: System.currentTimeMillis(),
    updatedAt = getEpochMillis("updatedAt") ?: System.currentTimeMillis()
)

private fun DocumentSnapshot.toFarmRecommendationEntity(userId: String): FarmRecommendationEntity? {
    val farmPath = getString("farmPath").orEmpty()
    val farmId = farmPath.substringAfterLast("/", missingDelimiterValue = "")
        .ifBlank { return null }
    val typeValue = getString("type") ?: return null
    return FarmRecommendationEntity(
        id = id,
        farmId = farmId,
        userId = userId,
        type = typeValue,
        title = getString("title").orEmpty(),
        message = getString("message").orEmpty(),
        priority = getString("priority")?.replaceFirstChar { it.uppercase() } ?: "Medium",
        suggestedItemName = getString("suggestedItemName")?.takeIf { it.isNotBlank() },
        alternativeItemName = getString("alternativeItemName")?.takeIf { it.isNotBlank() },
        source = getString("source") ?: "calendar",
        activityId = getString("activityId"),
        stage = getString("stage"),
        dayOfSeason = getLong("dayOfSeason")?.toInt(),
        suggestedQuantity = getDouble("suggestedQuantity"),
        quantityUnit = getString("quantityUnit"),
        activityStatus = getString("activityStatus"),
        confidence = getLong("confidence")?.toInt(),
        issueSignal = getString("issueSignal"),
        agriculturalNeed = getString("agriculturalNeed"),
        recommendedAction = getString("recommendedAction"),
        productCategory = getString("productCategory"),
        rationale = getString("rationale"),
        syncStatus = SyncStatus.SYNCED,
        remoteId = id,
        createdAt = getEpochMillis("createdAt") ?: System.currentTimeMillis(),
        updatedAt = getEpochMillis("updatedAt") ?: System.currentTimeMillis()
    )
}

private fun DocumentSnapshot.toSensorReadingEntity(userId: String): SensorReadingEntity = SensorReadingEntity(
    id = getString("id").takeUnless { it.isNullOrBlank() } ?: id,
    farmId = getString("farmId").orEmpty(),
    userId = userId,
    deviceId = getString("deviceId"),
    soilMoisturePercent = getDouble("soilMoisturePercent") ?: 0.0,
    temperatureCelsius = getDouble("temperatureCelsius") ?: 0.0,
    humidityPercent = getDouble("humidityPercent") ?: 0.0,
    lightIntensityLux = getDouble("lightIntensityLux") ?: 0.0,
    waterLevelPercent = getDouble("waterLevelPercent") ?: 0.0,
    status = getString("status").orEmpty(),
    source = getString("source").takeUnless { it.isNullOrBlank() } ?: "simulated",
    syncStatus = SyncStatus.SYNCED,
    remoteId = id,
    recordedAt = getEpochMillis("recordedAt") ?: System.currentTimeMillis(),
    updatedAt = getEpochMillis("updatedAt") ?: System.currentTimeMillis()
)

private fun DocumentSnapshot.toHarvestListingEntity(): HarvestListingEntity = HarvestListingEntity(
    id = id,
    farmId = getString("farmId").orEmpty(),
    farmPath = getString("farmPath").orEmpty(),
    farmName = getString("farmName").orEmpty(),
    officerUid = getString("officerUid").orEmpty(),
    cropType = getString("cropType").orEmpty(),
    locationText = getString("locationText").orEmpty(),
    district = getString("district").orEmpty(),
    estimatedQuantityMin = getDouble("estimatedQuantityMin"),
    estimatedQuantityMax = getDouble("estimatedQuantityMax"),
    quantityUnit = getString("quantityUnit").orEmpty().ifBlank { "tonnes" },
    harvestWindowStartDay = getLong("harvestWindowStartDay")?.toInt(),
    harvestWindowEndDay = getLong("harvestWindowEndDay")?.toInt(),
    harvestPeriodLabel = getString("harvestPeriodLabel").orEmpty(),
    qualityNote = getString("qualityNote").orEmpty(),
    confidence = getLong("confidence")?.toInt(),
    reliabilityLabel = getString("reliabilityLabel").orEmpty(),
    predictionSource = getString("predictionSource").orEmpty().ifBlank { "heuristic" },
    sourceRecommendationId = getString("sourceRecommendationId"),
    listingOrigin = getString("listingOrigin").orEmpty().ifBlank { "prediction" },
    status = getString("status").orEmpty().ifBlank { "listed" },
    visibility = getString("visibility").orEmpty().ifBlank { "public" },
    active = getBoolean("active") ?: true,
    verified = getBoolean("verified") ?: false,
    adminNote = getString("adminNote"),
    syncStatus = SyncStatus.SYNCED,
    remoteId = id,
    createdAt = getEpochMillis("createdAt") ?: System.currentTimeMillis(),
    updatedAt = getEpochMillis("updatedAt") ?: System.currentTimeMillis()
)

private fun DocumentSnapshot.toHarvestRequestEntity(): HarvestRequestEntity = HarvestRequestEntity(
    id = id,
    harvestListingId = getString("harvestListingId").orEmpty(),
    buyerId = getString("buyerId").orEmpty(),
    buyerUid = getString("buyerUid").orEmpty(),
    buyerName = getString("buyerName").orEmpty(),
    farmId = getString("farmId"),
    farmPath = getString("farmPath").orEmpty(),
    farmName = getString("farmName").orEmpty(),
    cropType = getString("cropType").orEmpty(),
    requestedQuantity = get("requestedQuantity")?.toString().orEmpty(),
    quantityUnit = getString("quantityUnit").orEmpty().ifBlank { "tonnes" },
    message = getString("message").orEmpty(),
    status = getString("status").orEmpty().ifBlank { "requested" },
    buyerNote = getString("buyerNote"),
    adminNote = getString("adminNote"),
    officerNote = getString("officerNote"),
    syncStatus = SyncStatus.SYNCED,
    remoteId = id,
    createdAt = getEpochMillis("createdAt") ?: System.currentTimeMillis(),
    updatedAt = getEpochMillis("updatedAt") ?: System.currentTimeMillis()
)

/**
 * Reads epoch millis from mixed Firestore date shapes.
 * Seed / older app writes may store numbers; admin / new writes use Timestamp.
 * Do not call [DocumentSnapshot.getTimestamp] first — it throws when the field is a number.
 */
private fun DocumentSnapshot.getEpochMillis(field: String): Long? {
    val value = get(field) ?: return null
    return when (value) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}

private fun Long.toFirestoreTimestamp(): Timestamp = Timestamp(Date(this))

private fun Long?.toFirestoreTimestampOrNull(): Timestamp? = this?.let { Timestamp(Date(it)) }

data class FarmIoTModuleStatus(
    val deviceId: String,
    val deviceType: String,
    val farmId: String?,
    val status: String,
    val lastSeen: Long?,
    val lastReadingAt: Long?,
    val lastCaptureAt: Long?,
    val signalStrength: Int?
)

data class FarmCameraCapture(
    val id: String,
    val deviceId: String,
    val farmId: String,
    val imageUrl: String?,
    val capturedAt: Long,
    val resolution: String?
)
