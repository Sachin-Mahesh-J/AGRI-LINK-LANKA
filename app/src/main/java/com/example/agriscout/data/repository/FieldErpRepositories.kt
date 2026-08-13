package com.example.agriscout.data.repository

import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.FarmVisitDao
import com.example.agriscout.data.local.FarmVisitEntity
import com.example.agriscout.data.local.InventoryItemDao
import com.example.agriscout.data.local.InventoryItemEntity
import com.example.agriscout.data.local.InventoryRequestDao
import com.example.agriscout.data.local.InventoryRequestEntity
import com.example.agriscout.data.local.SensorReadingDao
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.SyncStatus
import com.example.agriscout.data.remote.CatalogRemoteService
import com.example.agriscout.data.simulation.IoTDataSource
import com.example.agriscout.data.simulation.SensorReadingSource
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import java.util.UUID

data class StockAvailability(
    val category: String,
    val availableStock: Int,
    val unit: String,
    val items: List<InventoryItemEntity> = emptyList(),
    val selectedItem: InventoryItemEntity? = null,
    val alternativeItem: String? = null,
    val isLiveData: Boolean = false,
    val lastUpdatedAt: Long? = null
)

class SensorRepository(
    private val sensorReadingDao: SensorReadingDao,
    private val iotDataSource: IoTDataSource,
    private val staleAfterMs: Long = LIVE_READING_STALE_MS
) {
    fun observeLatestForFarm(farmId: String): Flow<SensorReadingEntity?> = sensorReadingDao.observeLatestForFarm(farmId)

    fun observeRecentForFarm(farmId: String): Flow<List<SensorReadingEntity>> = sensorReadingDao.observeRecentForFarm(farmId)

    /**
     * Prefers live device readings when present. Falls back to simulation so the
     * dashboard always has something to show while waiting for ingest/sync.
     * Returns Pair(reading, wroteNew) so callers can avoid redundant sync work.
     */
    suspend fun refreshReading(farm: FarmEntity, forceSimulation: Boolean = false): RefreshResult {
        val latest = sensorReadingDao.getLatestForFarm(farm.id)
        if (!forceSimulation && isLiveSource(latest)) {
            // Keep showing device data (fresh or stale) instead of overwriting with simulation.
            return RefreshResult(latest!!, wroteNew = false)
        }
        val reading = refreshSimulatedReading(farm)
        return RefreshResult(reading, wroteNew = true)
    }

    data class RefreshResult(
        val reading: SensorReadingEntity,
        val wroteNew: Boolean
    )

    suspend fun refreshSimulatedReading(farm: FarmEntity): SensorReadingEntity {
        val reading = iotDataSource.generateReading(farm)
        sensorReadingDao.upsert(reading)
        return reading
    }

    fun isReadingStale(reading: SensorReadingEntity?, now: Long = System.currentTimeMillis()): Boolean {
        if (reading == null) return true
        return now - reading.recordedAt > staleAfterMs
    }

    fun isLiveSource(reading: SensorReadingEntity?): Boolean =
        reading?.source.equals(SensorReadingSource.DEVICE, ignoreCase = true) == true

    companion object {
        const val LIVE_READING_STALE_MS = 60 * 1000L
    }
}

class InventoryRepository(
    private val inventoryRequestDao: InventoryRequestDao,
    private val inventoryItemDao: InventoryItemDao,
    private val catalogRemoteService: CatalogRemoteService
) {
    fun observeRequests(userId: String): Flow<List<InventoryRequestEntity>> = inventoryRequestDao.observeRequests(userId)

    fun observeItems(): Flow<List<InventoryItemEntity>> = inventoryItemDao.observeItems()

    suspend fun claimLegacyLocalData(userId: String) {
        inventoryRequestDao.claimLegacyRequests(
            legacyUserId = AuthRepository.LOCAL_USER_ID,
            newUserId = userId,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun refreshItemsFromRemote() {
        val remoteItems = catalogRemoteService.fetchInventoryItems()
        inventoryItemDao.clearAll()
        inventoryItemDao.upsertAll(remoteItems)
    }

    fun stockFor(
        itemType: String,
        inventoryItemId: String? = null,
        cachedItems: List<InventoryItemEntity> = emptyList()
    ): StockAvailability {
        val category = normalizeCategory(itemType)
        val categoryItems = cachedItems.filter { item ->
            item.category.equals(category, ignoreCase = true)
        }
        if (categoryItems.isEmpty()) {
            return StockAvailability(
                category = category,
                availableStock = 0,
                unit = "units",
                isLiveData = false
            )
        }

        val selectedItem = inventoryItemId
            ?.let { id -> categoryItems.firstOrNull { it.id == id } }
        val availableStock = selectedItem?.quantity ?: categoryItems.sumOf { it.quantity }
        val unit = selectedItem?.unit ?: categoryItems.first().unit
        val alternativeItem = resolveAlternative(
            selectedItem = selectedItem,
            categoryItems = categoryItems,
            allItems = cachedItems
        )

        return StockAvailability(
            category = category,
            availableStock = availableStock,
            unit = unit,
            items = categoryItems,
            selectedItem = selectedItem,
            alternativeItem = alternativeItem,
            isLiveData = true,
            lastUpdatedAt = categoryItems.maxOfOrNull { it.updatedAt }
        )
    }

    suspend fun submitRequest(
        userId: String,
        farmId: String?,
        itemType: String,
        quantity: String,
        reason: String,
        inventoryItemId: String? = null
    ) {
        val cachedItems = inventoryItemDao.getItems()
        val stock = stockFor(itemType, inventoryItemId, cachedItems)
        val selectedItem = stock.selectedItem
            ?: inventoryItemId?.let { id -> cachedItems.firstOrNull { it.id == id } }
        val now = System.currentTimeMillis()
        inventoryRequestDao.upsert(
            InventoryRequestEntity(
                id = UUID.randomUUID().toString(),
                userId = userId,
                farmId = farmId?.takeIf { it.isNotBlank() },
                itemType = stock.category,
                quantity = quantity.trim(),
                reason = reason.trim(),
                status = "Pending",
                availableStock = stock.availableStock,
                alternativeItem = stock.alternativeItem?.takeIf { stock.availableStock <= 0 },
                inventoryItemId = selectedItem?.id ?: inventoryItemId,
                itemName = selectedItem?.name,
                syncStatus = SyncStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun normalizeCategory(itemType: String): String {
        val normalized = itemType.trim().lowercase(Locale.getDefault())
        return when {
            "fertilizer" in normalized -> "Fertilizers"
            "chemical" in normalized -> "Chemicals"
            "seed" in normalized -> "Seeds"
            "equipment" in normalized -> "Equipment"
            itemType.isBlank() -> "Other"
            else -> itemType.trim()
        }
    }

    private fun resolveAlternative(
        selectedItem: InventoryItemEntity?,
        categoryItems: List<InventoryItemEntity>,
        allItems: List<InventoryItemEntity>
    ): String? {
        if (selectedItem != null && selectedItem.quantity > 0) return null
        if (selectedItem == null && categoryItems.any { it.quantity > 0 }) return null

        val preferredAlternatives = selectedItem
            ?.alternativeItemIds
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        preferredAlternatives
            .mapNotNull { id -> allItems.firstOrNull { it.id == id && it.quantity > 0 } }
            .firstOrNull()
            ?.let { return it.name }

        return categoryItems
            .filter { it.quantity > 0 && it.id != selectedItem?.id }
            .maxByOrNull { it.quantity }
            ?.name
    }
}

class FarmVisitRepository(
    private val farmVisitDao: FarmVisitDao
) {
    fun observeVisits(userId: String): Flow<List<FarmVisitEntity>> = farmVisitDao.observeVisits(userId)

    fun observeVisitsForFarm(farmId: String): Flow<List<FarmVisitEntity>> = farmVisitDao.observeVisitsForFarm(farmId)

    suspend fun claimLegacyLocalData(userId: String) {
        farmVisitDao.claimLegacyVisits(
            legacyUserId = AuthRepository.LOCAL_USER_ID,
            newUserId = userId,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun saveVisit(
        userId: String,
        farmId: String,
        cropCondition: String,
        notes: String,
        cropConditionDetail: String = "",
        pestObservations: String = "",
        growthStage: String = "",
        recommendedActions: String = "",
        followUpNotes: String = "",
        photoLocalUri: String? = null,
        remotePhotoUrl: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        gpsAccuracyMeters: Double? = null,
        gpsCapturedAt: Long? = null,
        gpsSource: String? = null
    ) {
        val now = System.currentTimeMillis()
        farmVisitDao.upsert(
            FarmVisitEntity(
                id = UUID.randomUUID().toString(),
                farmId = farmId,
                userId = userId,
                notes = notes.trim(),
                cropCondition = cropCondition.trim(),
                cropConditionDetail = cropConditionDetail.trim(),
                pestObservations = pestObservations.trim(),
                growthStage = growthStage.trim(),
                recommendedActions = recommendedActions.trim(),
                followUpNotes = followUpNotes.trim(),
                photoLocalUri = photoLocalUri,
                remotePhotoUrl = remotePhotoUrl,
                latitude = latitude,
                longitude = longitude,
                gpsAccuracyMeters = gpsAccuracyMeters,
                gpsCapturedAt = gpsCapturedAt,
                gpsSource = gpsSource,
                syncStatus = SyncStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
