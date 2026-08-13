package com.example.agriscout.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}

@Entity(tableName = "farms")
data class FarmEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val farmName: String,
    val farmerName: String,
    val cropType: String,
    val locationText: String,
    val landSize: String,
    val notes: String,
    val ownerUserId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Double? = null,
    val gpsCapturedAt: Long? = null,
    val gpsSource: String? = null,
    val plantingDate: Long? = null,
    val assignedDeviceId: String? = null,
    /** Optional ESP32-CAM linked to the same farm (separate from sensor). */
    val assignedCameraDeviceId: String? = null,
    val photoLocalUri: String? = null,
    val remotePhotoUrl: String? = null,
    val syncStatus: String = SyncStatus.PENDING,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "field_reports",
    foreignKeys = [
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("farmId"), Index("userId")]
)
data class FieldReportEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val userId: String,
    val cropType: String,
    val symptoms: String,
    val severity: String,
    val estimatedYield: String,
    val notes: String,
    val pestObservations: String = "",
    val growthStage: String = "",
    val cropConditionDetail: String = "",
    val recommendedActions: String = "",
    val followUpNotes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Double? = null,
    val gpsCapturedAt: Long? = null,
    val gpsSource: String? = null,
    val imageLocalUri: String? = null,
    val remoteImageUrl: String? = null,
    val issueType: String? = null,
    val detectedIssue: String? = null,
    val detectionConfidence: Int? = null,
    val matchedRuleId: String? = null,
    val recommendation: String? = null,
    val preventiveMeasures: String? = null,
    val detectionExplanation: String? = null,
    val detectionSource: String? = null,
    val detectionUpdatedAt: Long? = null,
    val syncStatus: String = SyncStatus.PENDING,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "farm_visits",
    foreignKeys = [
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("farmId"), Index("userId")]
)
data class FarmVisitEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val userId: String,
    val notes: String,
    val cropCondition: String,
    val cropConditionDetail: String = "",
    val pestObservations: String = "",
    val growthStage: String = "",
    val recommendedActions: String = "",
    val followUpNotes: String = "",
    val photoLocalUri: String? = null,
    val remotePhotoUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Double? = null,
    val gpsCapturedAt: Long? = null,
    val gpsSource: String? = null,
    val syncStatus: String = SyncStatus.PENDING,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "farm_recommendations",
    foreignKeys = [
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("farmId"), Index("userId")]
)
data class FarmRecommendationEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val userId: String,
    val type: String,
    val title: String,
    val message: String,
    val priority: String,
    val suggestedItemName: String? = null,
    val alternativeItemName: String? = null,
    val source: String = "calendar",
    val activityId: String? = null,
    val stage: String? = null,
    val dayOfSeason: Int? = null,
    val suggestedQuantity: Double? = null,
    val quantityUnit: String? = null,
    val activityStatus: String? = null,
    val confidence: Int? = null,
    val issueSignal: String? = null,
    val agriculturalNeed: String? = null,
    val recommendedAction: String? = null,
    val productCategory: String? = null,
    val rationale: String? = null,
    val syncStatus: String = SyncStatus.PENDING,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "sensor_readings",
    foreignKeys = [
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("farmId"), Index("userId"), Index("recordedAt")]
)
data class SensorReadingEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val userId: String,
    val deviceId: String? = null,
    val soilMoisturePercent: Double,
    val temperatureCelsius: Double,
    val humidityPercent: Double,
    val lightIntensityLux: Double,
    val waterLevelPercent: Double,
    val status: String,
    /** `simulated` or `device` — see [com.example.agriscout.data.simulation.SensorReadingSource]. */
    val source: String = "simulated",
    val syncStatus: String = SyncStatus.PENDING,
    val remoteId: String? = null,
    val recordedAt: Long,
    val updatedAt: Long = recordedAt
)

@Entity(
    tableName = "inventory_requests",
    foreignKeys = [
        ForeignKey(
            entity = FarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("farmId"), Index("userId")]
)
data class InventoryRequestEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val farmId: String?,
    val itemType: String,
    val quantity: String,
    val reason: String,
    val status: String,
    val availableStock: Int,
    val alternativeItem: String?,
    val inventoryItemId: String? = null,
    val itemName: String? = null,
    val approvalNote: String? = null,
    val reviewedAt: Long? = null,
    val approvedAt: Long? = null,
    val issuedAt: Long? = null,
    val issuedQuantity: Double? = null,
    val syncStatus: String = SyncStatus.PENDING,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val quantity: Int,
    val reorderLevel: Int,
    val unit: String,
    val alternativeItemIds: String = "",
    val updatedAt: Long
)

@Entity(tableName = "disease_catalog")
data class DiseaseCatalogEntity(
    @PrimaryKey val id: String,
    val diseaseName: String,
    val cropAffected: String,
    val symptoms: String,
    val treatment: String,
    val prevention: String,
    val severityGuidance: String,
    val updatedAt: Long
)

@Entity(tableName = "weather_warnings")
data class WeatherWarningEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val affectedArea: String,
    val severity: String,
    val validUntil: Long,
    val updatedAt: Long,
    val source: String = "FIRESTORE",
    val actionRoute: String? = null
)

@Entity(tableName = "weather_snapshots")
data class WeatherSnapshotEntity(
    @PrimaryKey val id: String,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val temperatureCelsius: Double,
    val humidityPercent: Int,
    val windSpeedMetersPerSecond: Double,
    val condition: String,
    val forecastSummary: String,
    val riskSummary: String,
    val fetchedAt: Long
)

@Entity(tableName = "fcm_tokens")
data class FcmTokenEntity(
    @PrimaryKey val token: String,
    val userId: String,
    val updatedAt: Long,
    val synced: Boolean = false
)

/** Verified supplier marketplace listing cached for offline recommendation matching. */
@Entity(
    tableName = "supplier_products",
    indices = [Index("category"), Index("supplierId")]
)
data class SupplierProductEntity(
    @PrimaryKey val id: String,
    val supplierId: String,
    val supplierName: String,
    val name: String,
    val category: String,
    val cropSuitability: String = "",
    val description: String = "",
    val unit: String = "units",
    val packSize: String = "",
    val price: Double? = null,
    val availabilityStatus: String = "available",
    val active: Boolean = true,
    val verified: Boolean = true,
    val updatedAt: Long
)

/** Officer request against a supplier product (separate from warehouse inventory_requests). */
@Entity(
    tableName = "product_requests",
    indices = [Index("userId"), Index("farmId"), Index("supplierProductId")]
)
data class ProductRequestEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val farmId: String?,
    val recommendationId: String? = null,
    val productCategory: String,
    val supplierProductId: String,
    val supplierId: String,
    val productName: String,
    val supplierName: String,
    val quantity: String,
    val unit: String = "units",
    val issueSignal: String? = null,
    val agriculturalNeed: String? = null,
    val recommendedAction: String? = null,
    val rationale: String? = null,
    val status: String = "created",
    val supplierNote: String? = null,
    val adminNote: String? = null,
    val syncStatus: String = SyncStatus.PENDING,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Upcoming harvest opportunity for the buyer marketplace.
 * Linked to a Phase 4 HARVEST recommendation when available.
 */
@Entity(
    tableName = "harvest_listings",
    indices = [Index("farmId"), Index("cropType"), Index("status")]
)
data class HarvestListingEntity(
    @PrimaryKey val id: String,
    val farmId: String,
    val farmPath: String = "",
    val farmName: String = "",
    val officerUid: String = "",
    val cropType: String,
    val locationText: String = "",
    val district: String = "",
    val estimatedQuantityMin: Double? = null,
    val estimatedQuantityMax: Double? = null,
    val quantityUnit: String = "tonnes",
    val harvestWindowStartDay: Int? = null,
    val harvestWindowEndDay: Int? = null,
    val harvestPeriodLabel: String = "",
    val qualityNote: String = "",
    val confidence: Int? = null,
    val reliabilityLabel: String = "",
    val predictionSource: String = "heuristic",
    val sourceRecommendationId: String? = null,
    val listingOrigin: String = "prediction",
    val status: String = "listed",
    val visibility: String = "public",
    val active: Boolean = true,
    val verified: Boolean = false,
    val adminNote: String? = null,
    val syncStatus: String = SyncStatus.PENDING,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/** Buyer interest / purchase request against a harvest listing (cached for officers). */
@Entity(
    tableName = "harvest_requests",
    indices = [Index("farmId"), Index("harvestListingId"), Index("status")]
)
data class HarvestRequestEntity(
    @PrimaryKey val id: String,
    val harvestListingId: String,
    val buyerId: String,
    val buyerUid: String = "",
    val buyerName: String = "",
    val farmId: String? = null,
    val farmPath: String = "",
    val farmName: String = "",
    val cropType: String = "",
    val requestedQuantity: String = "",
    val quantityUnit: String = "tonnes",
    val message: String = "",
    val status: String = "requested",
    val buyerNote: String? = null,
    val adminNote: String? = null,
    val officerNote: String? = null,
    val syncStatus: String = SyncStatus.SYNCED,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
