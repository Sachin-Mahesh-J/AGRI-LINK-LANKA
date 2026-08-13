package com.example.agriscout.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmDao {
    @Query("SELECT * FROM farms WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeFarms(userId: String): Flow<List<FarmEntity>>

    @Query("SELECT * FROM farms WHERE id = :id LIMIT 1")
    fun observeFarm(id: String): Flow<FarmEntity?>

    @Query("SELECT * FROM farms WHERE id = :id LIMIT 1")
    suspend fun getFarm(id: String): FarmEntity?

    @Query("SELECT * FROM farms WHERE userId = :userId AND syncStatus != :synced")
    suspend fun pendingFarms(userId: String, synced: String = SyncStatus.SYNCED): List<FarmEntity>

    @Query("SELECT COUNT(*) FROM farms WHERE userId = :userId")
    fun observeFarmCount(userId: String): Flow<Int>

    @Upsert
    suspend fun upsert(farm: FarmEntity)

    @Query("UPDATE farms SET userId = :newUserId, syncStatus = :pending, updatedAt = :updatedAt WHERE userId = :legacyUserId")
    suspend fun claimLegacyFarms(
        legacyUserId: String,
        newUserId: String,
        updatedAt: Long,
        pending: String = SyncStatus.PENDING
    )

    @Delete
    suspend fun delete(farm: FarmEntity)
}

@Dao
interface FieldReportDao {
    @Query("SELECT * FROM field_reports WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeReports(userId: String): Flow<List<FieldReportEntity>>

    @Query("SELECT * FROM field_reports WHERE farmId = :farmId ORDER BY updatedAt DESC")
    fun observeReportsForFarm(farmId: String): Flow<List<FieldReportEntity>>

    @Query("SELECT * FROM field_reports WHERE id = :id LIMIT 1")
    fun observeReport(id: String): Flow<FieldReportEntity?>

    @Query("SELECT * FROM field_reports WHERE id = :id LIMIT 1")
    suspend fun getReport(id: String): FieldReportEntity?

    @Query("SELECT * FROM field_reports WHERE userId = :userId AND syncStatus != :synced")
    suspend fun pendingReports(userId: String, synced: String = SyncStatus.SYNCED): List<FieldReportEntity>

    @Query("SELECT COUNT(*) FROM field_reports WHERE userId = :userId")
    fun observeReportCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM field_reports WHERE userId = :userId AND syncStatus != :synced")
    fun observePendingReportCount(userId: String, synced: String = SyncStatus.SYNCED): Flow<Int>

    @Upsert
    suspend fun upsert(report: FieldReportEntity)

    @Query("UPDATE field_reports SET userId = :newUserId, syncStatus = :pending, updatedAt = :updatedAt WHERE userId = :legacyUserId")
    suspend fun claimLegacyReports(
        legacyUserId: String,
        newUserId: String,
        updatedAt: Long,
        pending: String = SyncStatus.PENDING
    )

    @Delete
    suspend fun delete(report: FieldReportEntity)
}

@Dao
interface FarmVisitDao {
    @Query("SELECT * FROM farm_visits WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeVisits(userId: String): Flow<List<FarmVisitEntity>>

    @Query("SELECT * FROM farm_visits WHERE farmId = :farmId ORDER BY createdAt DESC")
    fun observeVisitsForFarm(farmId: String): Flow<List<FarmVisitEntity>>

    @Query("SELECT * FROM farm_visits WHERE id = :id LIMIT 1")
    suspend fun getVisit(id: String): FarmVisitEntity?

    @Query("SELECT * FROM farm_visits WHERE userId = :userId AND syncStatus != :synced")
    suspend fun pendingVisits(userId: String, synced: String = SyncStatus.SYNCED): List<FarmVisitEntity>

    @Upsert
    suspend fun upsert(visit: FarmVisitEntity)

    @Query("UPDATE farm_visits SET userId = :newUserId, syncStatus = :pending, updatedAt = :updatedAt WHERE userId = :legacyUserId")
    suspend fun claimLegacyVisits(
        legacyUserId: String,
        newUserId: String,
        updatedAt: Long,
        pending: String = SyncStatus.PENDING
    )
}

@Dao
interface FarmRecommendationDao {
    @Query("SELECT * FROM farm_recommendations WHERE farmId = :farmId ORDER BY updatedAt DESC")
    fun observeForFarm(farmId: String): Flow<List<FarmRecommendationEntity>>

    @Query("SELECT * FROM farm_recommendations WHERE userId = :userId AND syncStatus != :synced")
    suspend fun pendingRecommendations(userId: String, synced: String = SyncStatus.SYNCED): List<FarmRecommendationEntity>

    @Query("DELETE FROM farm_recommendations WHERE farmId = :farmId AND userId = :userId")
    suspend fun deleteForFarm(userId: String, farmId: String)

    @Query("SELECT * FROM farm_recommendations WHERE id = :id LIMIT 1")
    suspend fun getRecommendation(id: String): FarmRecommendationEntity?

    @Upsert
    suspend fun upsertAll(recommendations: List<FarmRecommendationEntity>)
}

@Dao
interface SensorReadingDao {
    @Query(
        """
        SELECT * FROM sensor_readings
        WHERE farmId = :farmId
        ORDER BY CASE WHEN LOWER(source) = 'device' THEN 0 ELSE 1 END, recordedAt DESC
        LIMIT 1
        """
    )
    fun observeLatestForFarm(farmId: String): Flow<SensorReadingEntity?>

    @Query(
        """
        SELECT * FROM sensor_readings
        WHERE farmId = :farmId
        ORDER BY CASE WHEN LOWER(source) = 'device' THEN 0 ELSE 1 END, recordedAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestForFarm(farmId: String): SensorReadingEntity?

    @Query("SELECT * FROM sensor_readings WHERE farmId = :farmId ORDER BY recordedAt DESC LIMIT :limit")
    fun observeRecentForFarm(farmId: String, limit: Int = 24): Flow<List<SensorReadingEntity>>

    @Query("SELECT * FROM sensor_readings WHERE id = :id LIMIT 1")
    suspend fun getReading(id: String): SensorReadingEntity?

    @Query("SELECT * FROM sensor_readings WHERE userId = :userId AND syncStatus != :synced")
    suspend fun pendingReadings(userId: String, synced: String = SyncStatus.SYNCED): List<SensorReadingEntity>

    @Query("SELECT * FROM sensor_readings WHERE userId = :userId ORDER BY recordedAt DESC")
    suspend fun readingsForUser(userId: String): List<SensorReadingEntity>

    @Upsert
    suspend fun upsert(reading: SensorReadingEntity)
}

@Dao
interface InventoryRequestDao {
    @Query("SELECT * FROM inventory_requests WHERE userId = :userId ORDER BY updatedAt DESC")
    fun observeRequests(userId: String): Flow<List<InventoryRequestEntity>>

    @Query("SELECT * FROM inventory_requests WHERE id = :id LIMIT 1")
    suspend fun getRequest(id: String): InventoryRequestEntity?

    @Query("SELECT * FROM inventory_requests WHERE userId = :userId AND syncStatus != :synced")
    suspend fun pendingRequests(userId: String, synced: String = SyncStatus.SYNCED): List<InventoryRequestEntity>

    @Upsert
    suspend fun upsert(request: InventoryRequestEntity)

    @Query("UPDATE inventory_requests SET status = :status, syncStatus = :pending, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun updateStatus(
        requestId: String,
        status: String,
        updatedAt: Long,
        pending: String = SyncStatus.PENDING
    )

    @Query("UPDATE inventory_requests SET userId = :newUserId, syncStatus = :pending, updatedAt = :updatedAt WHERE userId = :legacyUserId")
    suspend fun claimLegacyRequests(
        legacyUserId: String,
        newUserId: String,
        updatedAt: Long,
        pending: String = SyncStatus.PENDING
    )
}

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_items ORDER BY category, name")
    fun observeItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items ORDER BY category, name")
    suspend fun getItems(): List<InventoryItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<InventoryItemEntity>)

    @Query("DELETE FROM inventory_items")
    suspend fun clearAll()
}

@Dao
interface DiseaseCatalogDao {
    @Query("SELECT * FROM disease_catalog ORDER BY diseaseName")
    fun observeCatalog(): Flow<List<DiseaseCatalogEntity>>

    @Query("SELECT * FROM disease_catalog ORDER BY diseaseName")
    suspend fun getCatalog(): List<DiseaseCatalogEntity>

    @Upsert
    suspend fun upsertAll(items: List<DiseaseCatalogEntity>)
}

@Dao
interface WeatherWarningDao {
    @Query("SELECT * FROM weather_warnings ORDER BY updatedAt DESC")
    fun observeWarnings(): Flow<List<WeatherWarningEntity>>

    @Upsert
    suspend fun upsertAll(items: List<WeatherWarningEntity>)
}

@Dao
interface WeatherSnapshotDao {
    @Query("SELECT * FROM weather_snapshots ORDER BY fetchedAt DESC LIMIT 1")
    fun observeLatestSnapshot(): Flow<WeatherSnapshotEntity?>

    @Query("SELECT * FROM weather_snapshots ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun latestSnapshot(): WeatherSnapshotEntity?

    @Upsert
    suspend fun upsert(snapshot: WeatherSnapshotEntity)
}

@Dao
interface FcmTokenDao {
    @Query("SELECT * FROM fcm_tokens WHERE userId = :userId AND synced = 0")
    suspend fun pendingTokens(userId: String): List<FcmTokenEntity>

    @Upsert
    suspend fun upsert(token: FcmTokenEntity)

    @Query("UPDATE fcm_tokens SET synced = 1 WHERE token = :token")
    suspend fun markSynced(token: String)
}

@Dao
interface SupplierProductDao {
    @Query(
        """
        SELECT * FROM supplier_products
        WHERE active = 1 AND verified = 1
        ORDER BY category, name
        """
    )
    fun observeActiveProducts(): Flow<List<SupplierProductEntity>>

    @Query(
        """
        SELECT * FROM supplier_products
        WHERE active = 1 AND verified = 1
        ORDER BY category, name
        """
    )
    suspend fun getActiveProducts(): List<SupplierProductEntity>

    @Query(
        """
        SELECT * FROM supplier_products
        WHERE active = 1 AND verified = 1 AND category = :category
        ORDER BY name
        """
    )
    suspend fun getByCategory(category: String): List<SupplierProductEntity>

    @Upsert
    suspend fun upsertAll(items: List<SupplierProductEntity>)

    @Query("DELETE FROM supplier_products")
    suspend fun clearAll()
}

@Dao
interface ProductRequestDao {
    @Query("SELECT * FROM product_requests WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeRequests(userId: String): Flow<List<ProductRequestEntity>>

    @Query("SELECT * FROM product_requests WHERE id = :id LIMIT 1")
    suspend fun getRequest(id: String): ProductRequestEntity?

    @Query("SELECT * FROM product_requests WHERE userId = :userId AND syncStatus != :synced")
    suspend fun pendingRequests(
        userId: String,
        synced: String = SyncStatus.SYNCED
    ): List<ProductRequestEntity>

    @Upsert
    suspend fun upsert(request: ProductRequestEntity)

    @Upsert
    suspend fun upsertAll(requests: List<ProductRequestEntity>)
}

@Dao
interface HarvestListingDao {
    @Query(
        """
        SELECT * FROM harvest_listings
        WHERE farmId = :farmId
        ORDER BY updatedAt DESC
        """
    )
    fun observeForFarm(farmId: String): Flow<List<HarvestListingEntity>>

    @Query(
        """
        SELECT * FROM harvest_listings
        WHERE officerUid = :officerUid
        ORDER BY updatedAt DESC
        """
    )
    fun observeForOfficer(officerUid: String): Flow<List<HarvestListingEntity>>

    @Query("SELECT * FROM harvest_listings WHERE id = :id LIMIT 1")
    suspend fun getListing(id: String): HarvestListingEntity?

    @Query(
        """
        SELECT * FROM harvest_listings
        WHERE officerUid = :officerUid AND syncStatus != :synced
        """
    )
    suspend fun pendingListings(
        officerUid: String,
        synced: String = SyncStatus.SYNCED
    ): List<HarvestListingEntity>

    @Query(
        """
        SELECT * FROM harvest_listings
        WHERE sourceRecommendationId = :recommendationId
        LIMIT 1
        """
    )
    suspend fun findByRecommendationId(recommendationId: String): HarvestListingEntity?

    @Upsert
    suspend fun upsert(listing: HarvestListingEntity)

    @Upsert
    suspend fun upsertAll(listings: List<HarvestListingEntity>)
}

@Dao
interface HarvestRequestDao {
    @Query(
        """
        SELECT * FROM harvest_requests
        WHERE farmId = :farmId
        ORDER BY createdAt DESC
        """
    )
    fun observeForFarm(farmId: String): Flow<List<HarvestRequestEntity>>

    @Query(
        """
        SELECT * FROM harvest_requests
        WHERE farmPath LIKE :farmPathPrefix
        ORDER BY createdAt DESC
        """
    )
    fun observeForOfficerPath(farmPathPrefix: String): Flow<List<HarvestRequestEntity>>

    @Query("SELECT * FROM harvest_requests WHERE id = :id LIMIT 1")
    suspend fun getRequest(id: String): HarvestRequestEntity?

    @Query(
        """
        SELECT * FROM harvest_requests
        WHERE syncStatus != :synced
        """
    )
    suspend fun pendingRequests(synced: String = SyncStatus.SYNCED): List<HarvestRequestEntity>

    @Upsert
    suspend fun upsert(request: HarvestRequestEntity)

    @Upsert
    suspend fun upsertAll(requests: List<HarvestRequestEntity>)
}
