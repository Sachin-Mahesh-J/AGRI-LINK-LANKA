package com.example.agriscout.data.repository

import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.HarvestListingDao
import com.example.agriscout.data.local.HarvestListingEntity
import com.example.agriscout.data.local.HarvestRequestDao
import com.example.agriscout.data.local.HarvestRequestEntity
import com.example.agriscout.data.local.ProductRequestDao
import com.example.agriscout.data.local.ProductRequestEntity
import com.example.agriscout.data.local.SupplierProductDao
import com.example.agriscout.data.local.SupplierProductEntity
import com.example.agriscout.data.local.SyncStatus
import com.example.agriscout.data.remote.CatalogRemoteService
import com.example.agriscout.recommendation.Recommendation
import com.example.agriscout.recommendation.RecommendationType
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class MarketplaceRepository(
    private val supplierProductDao: SupplierProductDao,
    private val productRequestDao: ProductRequestDao,
    private val harvestListingDao: HarvestListingDao,
    private val harvestRequestDao: HarvestRequestDao,
    private val catalogRemoteService: CatalogRemoteService
) {
    fun observeSupplierProducts(): Flow<List<SupplierProductEntity>> =
        supplierProductDao.observeActiveProducts()

    fun observeProductRequests(userId: String): Flow<List<ProductRequestEntity>> =
        productRequestDao.observeRequests(userId)

    fun observeHarvestListings(farmId: String): Flow<List<HarvestListingEntity>> =
        harvestListingDao.observeForFarm(farmId)

    fun observeHarvestListingsForOfficer(officerUid: String): Flow<List<HarvestListingEntity>> =
        harvestListingDao.observeForOfficer(officerUid)

    fun observeHarvestRequests(farmId: String): Flow<List<HarvestRequestEntity>> =
        harvestRequestDao.observeForFarm(farmId)

    fun observeHarvestRequestsForOfficer(officerUid: String): Flow<List<HarvestRequestEntity>> =
        harvestRequestDao.observeForOfficerPath("users/$officerUid/farms/%")

    suspend fun refreshSupplierProductsFromRemote() {
        val remote = catalogRemoteService.fetchSupplierProducts()
        supplierProductDao.clearAll()
        supplierProductDao.upsertAll(remote)
    }

    suspend fun refreshHarvestMarketplaceFromRemote(officerUid: String) {
        val listings = catalogRemoteService.fetchHarvestListingsForOfficer(officerUid)
        listings.forEach { remote ->
            val local = harvestListingDao.getListing(remote.id)
            if (local?.syncStatus == SyncStatus.PENDING) {
                return@forEach
            }
            harvestListingDao.upsert(remote.copy(syncStatus = SyncStatus.SYNCED))
        }
        val requests = catalogRemoteService.fetchHarvestRequestsForOfficer(officerUid)
        requests.forEach { remote ->
            val local = harvestRequestDao.getRequest(remote.id)
            if (local?.syncStatus == SyncStatus.PENDING) {
                return@forEach
            }
            harvestRequestDao.upsert(remote.copy(syncStatus = SyncStatus.SYNCED))
        }
    }

    suspend fun createProductRequest(
        userId: String,
        farmId: String?,
        recommendation: Recommendation?,
        product: SupplierProductEntity,
        quantity: String
    ) {
        val now = System.currentTimeMillis()
        productRequestDao.upsert(
            ProductRequestEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                farmId = farmId?.takeIf { it.isNotBlank() },
                recommendationId = null,
                productCategory = recommendation?.productCategory ?: product.category,
                supplierProductId = product.id,
                supplierId = product.supplierId,
                productName = product.name,
                supplierName = product.supplierName,
                quantity = quantity.trim().ifBlank { "1" },
                unit = product.unit,
                issueSignal = recommendation?.issueSignal,
                agriculturalNeed = recommendation?.agriculturalNeed,
                recommendedAction = recommendation?.recommendedAction,
                rationale = recommendation?.rationale,
                status = "created",
                syncStatus = SyncStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun cancelProductRequest(requestId: String) {
        val existing = productRequestDao.getRequest(requestId)
            ?: error("Supplier product request not found.")
        require(existing.status.equals("created", ignoreCase = true)) {
            "Only newly created supplier requests can be cancelled."
        }
        val now = System.currentTimeMillis()
        productRequestDao.upsert(
            existing.copy(
                status = "cancelled",
                syncStatus = SyncStatus.PENDING,
                updatedAt = now
            )
        )
    }

    /**
     * Officer response to buyer interest. Allowed statuses: accepted / rejected / under_review.
     */
    suspend fun respondToHarvestRequest(
        requestId: String,
        status: String,
        officerNote: String?
    ) {
        val normalized = status.trim().lowercase()
        require(normalized in setOf("accepted", "rejected", "under_review")) {
            "Unsupported harvest response status: $status"
        }
        val existing = harvestRequestDao.getRequest(requestId)
            ?: error("Harvest request not found.")
        val now = System.currentTimeMillis()
        harvestRequestDao.upsert(
            existing.copy(
                status = normalized,
                officerNote = officerNote?.trim()?.takeIf { it.isNotEmpty() },
                syncStatus = SyncStatus.PENDING,
                updatedAt = now
            )
        )
    }

    /**
     * Creates a local harvest listing from a HARVEST recommendation.
     * Idempotent per recommendation document id to avoid duplicate marketplace rows.
     */
    suspend fun publishHarvestListing(
        officerUid: String,
        farm: FarmEntity,
        recommendation: Recommendation,
        recommendationId: String? = null
    ): HarvestListingEntity {
        require(recommendation.type == RecommendationType.HARVEST) {
            "Only HARVEST recommendations can be published as harvest listings."
        }
        val recommendationKey = recommendationId?.takeIf { it.isNotBlank() }
            ?: recommendationDocumentId(officerUid, farm.id, recommendation)
        harvestListingDao.findByRecommendationId(recommendationKey)?.let { return it }

        val now = System.currentTimeMillis()
        val district = farm.locationText
            .split(",")
            .map { it.trim() }
            .lastOrNull()
            .orEmpty()
            .ifBlank { farm.locationText }
        val confidence = recommendation.confidence
        val reliability = when {
            confidence == null -> ""
            confidence >= 70 -> "Moderate"
            confidence >= 45 -> "Limited"
            else -> "Low"
        }
        val listing = HarvestListingEntity(
            id = recommendationKey,
            farmId = farm.id,
            farmPath = "users/$officerUid/farms/${farm.id}",
            farmName = farm.farmName,
            officerUid = officerUid,
            cropType = farm.cropType,
            locationText = farm.locationText,
            district = district,
            estimatedQuantityMin = null,
            estimatedQuantityMax = recommendation.suggestedQuantity,
            quantityUnit = recommendation.quantityUnit ?: "tonnes",
            harvestPeriodLabel = recommendation.activityStatus.orEmpty(),
            qualityNote = recommendation.rationale.orEmpty(),
            confidence = confidence,
            reliabilityLabel = reliability,
            predictionSource = recommendation.source.ifBlank { "heuristic" },
            sourceRecommendationId = recommendationKey,
            listingOrigin = "prediction",
            status = "listed",
            visibility = "public",
            active = true,
            verified = false,
            syncStatus = SyncStatus.PENDING,
            createdAt = now,
            updatedAt = now
        )
        harvestListingDao.upsert(listing)
        return listing
    }
}
