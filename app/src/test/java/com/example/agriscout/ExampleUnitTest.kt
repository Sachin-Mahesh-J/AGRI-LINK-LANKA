package com.example.agriscout

import com.example.agriscout.auth.FirebaseAuthService
import com.example.agriscout.data.local.FarmDao
import com.example.agriscout.data.local.FarmRecommendationDao
import com.example.agriscout.data.local.FarmRecommendationEntity
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.FarmVisitDao
import com.example.agriscout.data.local.FarmVisitEntity
import com.example.agriscout.data.local.FieldReportDao
import com.example.agriscout.data.local.FieldReportEntity
import com.example.agriscout.data.local.HarvestListingDao
import com.example.agriscout.data.local.HarvestListingEntity
import com.example.agriscout.data.local.HarvestRequestDao
import com.example.agriscout.data.local.HarvestRequestEntity
import com.example.agriscout.data.local.InventoryItemDao
import com.example.agriscout.data.local.InventoryItemEntity
import com.example.agriscout.data.local.InventoryRequestDao
import com.example.agriscout.data.local.InventoryRequestEntity
import com.example.agriscout.data.local.ProductRequestDao
import com.example.agriscout.data.local.ProductRequestEntity
import com.example.agriscout.data.local.SensorReadingDao
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.SyncStatus
import com.example.agriscout.data.remote.CatalogRemoteService
import com.example.agriscout.data.remote.SyncRemoteService
import com.example.agriscout.data.repository.FarmVisitRepository
import com.example.agriscout.data.repository.FarmRepository
import com.example.agriscout.data.repository.InventoryRepository
import com.example.agriscout.data.repository.ReportRepository
import com.example.agriscout.data.repository.SyncRepository
import com.example.agriscout.detection.DetectionResult
import com.example.agriscout.detection.IssueTypes
import com.example.agriscout.sync.ConnectivityChecker
import com.example.agriscout.sync.SyncConflictPolicy
import com.example.agriscout.ui.viewmodel.AuthFormState
import com.example.agriscout.ui.viewmodel.FarmFormState
import com.example.agriscout.ui.viewmodel.ReportFormState
import com.example.agriscout.ui.viewmodel.locationCaptureMessage
import com.example.agriscout.ui.viewmodel.officerAccessFromStatus
import com.example.agriscout.ui.viewmodel.validateAuthForm
import com.example.agriscout.ui.viewmodel.validateFarmForm
import com.example.agriscout.ui.viewmodel.validateReportForm
import com.example.agriscout.auth.OfficerAccessStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class AgriScoutQualityTest {

    @Test
    fun validationMessagesExplainWhatToFix() {
        assertEquals("Enter a valid officer email address.", validateAuthForm(AuthFormState(email = "officer", password = "123456")))
        assertEquals("Enter a password with at least 6 characters.", validateAuthForm(AuthFormState(email = "officer@example.com", password = "123")))
        assertEquals("Farm name is required.", validateFarmForm(FarmFormState(farmerName = "Asha", cropType = "Rice", locationText = "Pune")))
        assertEquals(
            "Select a farm before saving this report.",
            validateReportForm(ReportFormState(cropType = "Rice", symptoms = "Leaf spots", severity = "High"))
        )
        assertNull(validateAuthForm(AuthFormState(email = "officer@example.com", password = "123456")))
    }

    @Test
    fun officerAccessGateDistinguishesPendingActiveAndInactive() {
        val pending = officerAccessFromStatus(role = "field_officer", status = OfficerAccessStatus.PENDING)
        assertFalse(pending.canOperate)
        assertFalse(pending.canSync)
        assertTrue(pending.message.contains("pending", ignoreCase = true))

        val active = officerAccessFromStatus(role = "field_officer", status = OfficerAccessStatus.ACTIVE)
        assertTrue(active.canOperate)
        assertTrue(active.canSync)

        val inactive = officerAccessFromStatus(role = "field_officer", status = OfficerAccessStatus.INACTIVE)
        assertFalse(inactive.canOperate)
        assertTrue(inactive.message.contains("inactive", ignoreCase = true))
    }

    @Test
    fun locationCaptureMessageIncludesAccuracyAndSource() {
        val message = locationCaptureMessage("Location", 12.4f, "device_gps")
        assertTrue(message.contains("±12 m"))
        assertTrue(message.contains("device GPS"))
    }

    @Test
    fun farmRepositoryTrimsAndMarksSavedFarmPending() = runBlocking {
        val dao = InMemoryFarmDao()
        val repository = FarmRepository(dao, FirebaseAuthService(null), SyncRemoteService(null, null))

        repository.saveFarm(
            existing = null,
            userId = "officer-1",
            farmName = "  North Field  ",
            farmerName = "  Meera  ",
            cropType = "  Wheat  ",
            locationText = "  Satara  ",
            landSize = "  4 acres  ",
            notes = "  Irrigated  "
        )

        val farm = dao.items.value.single()
        assertEquals("North Field", farm.farmName)
        assertEquals("Meera", farm.farmerName)
        assertEquals("Wheat", farm.cropType)
        assertEquals(SyncStatus.PENDING, farm.syncStatus)
        assertNotNull(farm.id)
    }

    @Test
    fun reportRepositoryClearsRemoteImageWhenLocalEvidenceChanges() = runBlocking {
        val dao = InMemoryReportDao()
        val repository = ReportRepository(dao, FirebaseAuthService(null), SyncRemoteService(null, null))
        val existing = FieldReportEntity(
            id = "report-1",
            farmId = "farm-1",
            userId = "officer-1",
            cropType = "Rice",
            symptoms = "Yellowing",
            severity = "Low",
            estimatedYield = "2 tons",
            notes = "Monitor",
            imageLocalUri = "file://old.jpg",
            remoteImageUrl = "https://example.com/old.jpg",
            syncStatus = SyncStatus.SYNCED,
            remoteId = "remote-report-1",
            createdAt = 10L,
            updatedAt = 10L
        )

        repository.saveReport(
            existing = existing,
            userId = "officer-1",
            farmId = "farm-1",
            cropType = "  Rice  ",
            symptoms = "  Brown spots  ",
            severity = "High",
            estimatedYield = "  2 tons  ",
            notes = "  Treat quickly  ",
            pestObservations = "  Leafhoppers on edges  ",
            growthStage = " Vegetative ",
            cropConditionDetail = "  Canopy thinning  ",
            recommendedActions = "  Apply approved fungicide  ",
            followUpNotes = "  Recheck in 5 days  ",
            latitude = 18.5,
            longitude = 73.8,
            gpsAccuracyMeters = 8.0,
            gpsCapturedAt = 20L,
            gpsSource = "device_gps",
            imageLocalUri = "file://new.jpg",
            detectionResult = DetectionResult(
                issueType = IssueTypes.DISEASE,
                issueName = "Rice Brown Spot",
                confidence = 90,
                matchedRuleId = "rice-brown-spot",
                matchedKeywords = listOf("brown spots"),
                treatment = "Correct nutrient stress.",
                prevention = "Use clean seed."
            )
        )

        val report = dao.items.value.single()
        assertEquals("Rice", report.cropType)
        assertEquals("Brown spots", report.symptoms)
        assertEquals("Leafhoppers on edges", report.pestObservations)
        assertEquals("Vegetative", report.growthStage)
        assertEquals("Canopy thinning", report.cropConditionDetail)
        assertEquals("Apply approved fungicide", report.recommendedActions)
        assertEquals("Recheck in 5 days", report.followUpNotes)
        assertEquals(8.0, report.gpsAccuracyMeters)
        assertEquals(20L, report.gpsCapturedAt)
        assertEquals("device_gps", report.gpsSource)
        assertEquals(SyncStatus.PENDING, report.syncStatus)
        assertNull(report.remoteImageUrl)
        assertEquals("remote-report-1", report.remoteId)
        assertEquals("Rice Brown Spot", report.detectedIssue)
        assertEquals(90, report.detectionConfidence)
        assertTrue(report.recommendation!!.contains("Correct nutrient stress."))
    }

    @Test
    fun localDeleteDoesNotRequireFirebaseSession() = runBlocking {
        val farmDao = InMemoryFarmDao()
        val reportDao = InMemoryReportDao()
        val farmRepository = FarmRepository(farmDao, FirebaseAuthService(null), SyncRemoteService(null, null))
        val reportRepository = ReportRepository(reportDao, FirebaseAuthService(null), SyncRemoteService(null, null))
        val farm = sampleFarm("farm-1")
        val report = sampleReport("report-1")
        farmDao.upsert(farm)
        reportDao.upsert(report)

        assertTrue(farmRepository.deleteFarm(farm))
        assertTrue(reportRepository.deleteReport(report))

        assertTrue(farmDao.items.value.isEmpty())
        assertTrue(reportDao.items.value.isEmpty())
    }

    @Test
    fun syncRepositoryMarksLoginRequiredWhenFirebaseUserIsMissing() = runBlocking {
        val repository = SyncRepository(
            farmDao = InMemoryFarmDao(),
            reportDao = InMemoryReportDao(),
            farmVisitDao = InMemoryFarmVisitDao(),
            farmRecommendationDao = InMemoryFarmRecommendationDao(),
            inventoryRequestDao = InMemoryInventoryRequestDao(),
            productRequestDao = InMemoryProductRequestDao(),
            harvestListingDao = InMemoryHarvestListingDao(),
            harvestRequestDao = InMemoryHarvestRequestDao(),
            sensorReadingDao = InMemorySensorReadingDao(),
            authService = FirebaseAuthService(null),
            remoteService = SyncRemoteService(null, null)
        )

        val result = repository.syncPendingData()

        assertTrue(result.requiresAuthentication)
        assertEquals("Login required for Firebase sync.", result.message)
        assertEquals(listOf("Login required for Firebase sync."), result.errors)
    }

    @Test
    fun syncRepositoryWaitsForConnectivityWithoutMarkingFailures() = runBlocking {
        val farmDao = InMemoryFarmDao()
        farmDao.upsert(sampleFarm("farm-offline").copy(syncStatus = SyncStatus.PENDING))
        val repository = SyncRepository(
            farmDao = farmDao,
            reportDao = InMemoryReportDao(),
            farmVisitDao = InMemoryFarmVisitDao(),
            farmRecommendationDao = InMemoryFarmRecommendationDao(),
            inventoryRequestDao = InMemoryInventoryRequestDao(),
            productRequestDao = InMemoryProductRequestDao(),
            harvestListingDao = InMemoryHarvestListingDao(),
            harvestRequestDao = InMemoryHarvestRequestDao(),
            sensorReadingDao = InMemorySensorReadingDao(),
            authService = FirebaseAuthService(null),
            remoteService = SyncRemoteService(null, null),
            connectivityChecker = ConnectivityChecker { false },
            currentUserIdProvider = { "officer-1" }
        )

        val result = repository.syncPendingData()

        assertTrue(result.waitingForConnectivity)
        assertEquals(0, result.failures)
        assertEquals(SyncStatus.PENDING, farmDao.items.value.single().syncStatus)
        assertTrue(result.message.contains("connectivity", ignoreCase = true))
    }

    @Test
    fun conflictPolicyProtectsPendingLocalEditsAndAcceptsNewerSyncedRemote() {
        assertTrue(
            SyncConflictPolicy.shouldApplyRemote(
                localSyncStatus = null,
                localUpdatedAt = 0L,
                remoteUpdatedAt = 10L
            )
        )
        assertFalse(
            SyncConflictPolicy.shouldApplyRemote(
                localSyncStatus = SyncStatus.PENDING,
                localUpdatedAt = 5L,
                remoteUpdatedAt = 20L
            )
        )
        assertFalse(
            SyncConflictPolicy.shouldApplyRemote(
                localSyncStatus = SyncStatus.FAILED,
                localUpdatedAt = 5L,
                remoteUpdatedAt = 20L
            )
        )
        assertFalse(
            SyncConflictPolicy.shouldApplyRemote(
                localSyncStatus = SyncStatus.SYNCED,
                localUpdatedAt = 20L,
                remoteUpdatedAt = 20L
            )
        )
        assertTrue(
            SyncConflictPolicy.shouldApplyRemote(
                localSyncStatus = SyncStatus.SYNCED,
                localUpdatedAt = 10L,
                remoteUpdatedAt = 20L
            )
        )
        assertTrue(SyncConflictPolicy.isConnectivityFailure(UnknownHostException("host")))
    }

    @Test
    fun inventoryRepositoryCreatesPendingRequestWithLiveStockSnapshot() = runBlocking {
        val requestDao = InMemoryInventoryRequestDao()
        val itemDao = InMemoryInventoryItemDao()
        itemDao.upsertAll(
            listOf(
                InventoryItemEntity(
                    id = "seed-1",
                    name = "Hybrid Rice Seed",
                    category = "Seeds",
                    quantity = 0,
                    reorderLevel = 10,
                    unit = "kg",
                    alternativeItemIds = "seed-2",
                    updatedAt = 1L
                ),
                InventoryItemEntity(
                    id = "seed-2",
                    name = "Certified hybrid seed lot B",
                    category = "Seeds",
                    quantity = 15,
                    reorderLevel = 5,
                    unit = "kg",
                    updatedAt = 1L
                )
            )
        )
        val repository = InventoryRepository(requestDao, itemDao, CatalogRemoteService(null))

        repository.submitRequest(
            userId = "officer-1",
            farmId = "farm-1",
            itemType = "Seeds",
            quantity = "5 kg",
            reason = "Replant damaged rows",
            inventoryItemId = "seed-1"
        )

        val request = requestDao.items.value.single()
        assertEquals("officer-1", request.userId)
        assertEquals("farm-1", request.farmId)
        assertEquals("Seeds", request.itemType)
        assertEquals("Pending", request.status)
        assertEquals(SyncStatus.PENDING, request.syncStatus)
        assertEquals("seed-1", request.inventoryItemId)
        assertEquals("Hybrid Rice Seed", request.itemName)
        assertEquals("Certified hybrid seed lot B", request.alternativeItem)
        assertEquals(0, request.availableStock)
    }

    @Test
    fun farmVisitRepositoryCreatesPendingVisitForSelectedFarm() = runBlocking {
        val dao = InMemoryFarmVisitDao()
        val repository = FarmVisitRepository(dao)

        repository.saveVisit(
            userId = "officer-1",
            farmId = "farm-1",
            cropCondition = " Watch ",
            notes = "  Minor yellowing near border  "
        )

        val visit = dao.items.value.single()
        assertEquals("officer-1", visit.userId)
        assertEquals("farm-1", visit.farmId)
        assertEquals("Watch", visit.cropCondition)
        assertEquals("Minor yellowing near border", visit.notes)
        assertEquals(SyncStatus.PENDING, visit.syncStatus)
    }

    private fun sampleFarm(id: String) = FarmEntity(
        id = id,
        userId = "officer-1",
        farmName = "North Field",
        farmerName = "Meera",
        cropType = "Wheat",
        locationText = "Satara",
        landSize = "4 acres",
        notes = "",
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun sampleReport(id: String) = FieldReportEntity(
        id = id,
        farmId = "farm-1",
        userId = "officer-1",
        cropType = "Wheat",
        symptoms = "Leaf spots",
        severity = "Medium",
        estimatedYield = "2 tons",
        notes = "",
        createdAt = 1L,
        updatedAt = 1L
    )

    private class InMemoryFarmDao : FarmDao {
        val items = MutableStateFlow<List<FarmEntity>>(emptyList())

        override fun observeFarms(userId: String): Flow<List<FarmEntity>> = items.map { farms -> farms.filter { it.userId == userId } }
        override fun observeFarm(id: String): Flow<FarmEntity?> = items.map { farms -> farms.firstOrNull { it.id == id } }
        override suspend fun getFarm(id: String): FarmEntity? = items.value.firstOrNull { it.id == id }
        override suspend fun pendingFarms(userId: String, synced: String): List<FarmEntity> =
            items.value.filter { it.userId == userId && it.syncStatus != synced }
        override fun observeFarmCount(userId: String): Flow<Int> = observeFarms(userId).map { it.size }
        override suspend fun upsert(farm: FarmEntity) {
            items.value = items.value.filterNot { it.id == farm.id } + farm
        }
        override suspend fun claimLegacyFarms(legacyUserId: String, newUserId: String, updatedAt: Long, pending: String) {
            items.value = items.value.map {
                if (it.userId == legacyUserId) it.copy(userId = newUserId, syncStatus = pending, updatedAt = updatedAt) else it
            }
        }
        override suspend fun delete(farm: FarmEntity) {
            items.value = items.value.filterNot { it.id == farm.id }
        }
    }

    private class InMemoryReportDao : FieldReportDao {
        val items = MutableStateFlow<List<FieldReportEntity>>(emptyList())

        override fun observeReports(userId: String): Flow<List<FieldReportEntity>> =
            items.map { reports -> reports.filter { it.userId == userId } }
        override fun observeReportsForFarm(farmId: String): Flow<List<FieldReportEntity>> =
            items.map { reports -> reports.filter { it.farmId == farmId } }
        override fun observeReport(id: String): Flow<FieldReportEntity?> = items.map { reports -> reports.firstOrNull { it.id == id } }
        override suspend fun getReport(id: String): FieldReportEntity? = items.value.firstOrNull { it.id == id }
        override suspend fun pendingReports(userId: String, synced: String): List<FieldReportEntity> =
            items.value.filter { it.userId == userId && it.syncStatus != synced }
        override fun observeReportCount(userId: String): Flow<Int> = observeReports(userId).map { it.size }
        override fun observePendingReportCount(userId: String, synced: String): Flow<Int> =
            observeReports(userId).map { reports -> reports.count { it.syncStatus != synced } }
        override suspend fun upsert(report: FieldReportEntity) {
            items.value = items.value.filterNot { it.id == report.id } + report
        }
        override suspend fun claimLegacyReports(legacyUserId: String, newUserId: String, updatedAt: Long, pending: String) {
            items.value = items.value.map {
                if (it.userId == legacyUserId) it.copy(userId = newUserId, syncStatus = pending, updatedAt = updatedAt) else it
            }
        }
        override suspend fun delete(report: FieldReportEntity) {
            items.value = items.value.filterNot { it.id == report.id }
        }
    }

    private class InMemoryFarmVisitDao : FarmVisitDao {
        val items = MutableStateFlow<List<FarmVisitEntity>>(emptyList())

        override fun observeVisits(userId: String): Flow<List<FarmVisitEntity>> =
            items.map { visits -> visits.filter { it.userId == userId } }
        override fun observeVisitsForFarm(farmId: String): Flow<List<FarmVisitEntity>> =
            items.map { visits -> visits.filter { it.farmId == farmId } }
        override suspend fun getVisit(id: String): FarmVisitEntity? = items.value.firstOrNull { it.id == id }
        override suspend fun pendingVisits(userId: String, synced: String): List<FarmVisitEntity> =
            items.value.filter { it.userId == userId && it.syncStatus != synced }
        override suspend fun upsert(visit: FarmVisitEntity) {
            items.value = items.value.filterNot { it.id == visit.id } + visit
        }
        override suspend fun claimLegacyVisits(legacyUserId: String, newUserId: String, updatedAt: Long, pending: String) {
            items.value = items.value.map {
                if (it.userId == legacyUserId) it.copy(userId = newUserId, syncStatus = pending, updatedAt = updatedAt) else it
            }
        }
    }

    private class InMemoryInventoryItemDao : InventoryItemDao {
        val items = MutableStateFlow<List<InventoryItemEntity>>(emptyList())

        override fun observeItems(): Flow<List<InventoryItemEntity>> = items
        override suspend fun getItems(): List<InventoryItemEntity> = items.value
        override suspend fun upsertAll(items: List<InventoryItemEntity>) {
            this.items.value = items
        }
        override suspend fun clearAll() {
            items.value = emptyList()
        }
    }

    private class InMemoryInventoryRequestDao : InventoryRequestDao {
        val items = MutableStateFlow<List<InventoryRequestEntity>>(emptyList())

        override fun observeRequests(userId: String): Flow<List<InventoryRequestEntity>> =
            items.map { requests -> requests.filter { it.userId == userId } }
        override suspend fun getRequest(id: String): InventoryRequestEntity? = items.value.firstOrNull { it.id == id }
        override suspend fun pendingRequests(userId: String, synced: String): List<InventoryRequestEntity> =
            items.value.filter { it.userId == userId && it.syncStatus != synced }
        override suspend fun upsert(request: InventoryRequestEntity) {
            items.value = items.value.filterNot { it.id == request.id } + request
        }
        override suspend fun updateStatus(requestId: String, status: String, updatedAt: Long, pending: String) {
            items.value = items.value.map {
                if (it.id == requestId) it.copy(status = status, syncStatus = pending, updatedAt = updatedAt) else it
            }
        }
        override suspend fun claimLegacyRequests(legacyUserId: String, newUserId: String, updatedAt: Long, pending: String) {
            items.value = items.value.map {
                if (it.userId == legacyUserId) it.copy(userId = newUserId, syncStatus = pending, updatedAt = updatedAt) else it
            }
        }
    }

    private class InMemoryProductRequestDao : ProductRequestDao {
        val items = MutableStateFlow<List<ProductRequestEntity>>(emptyList())

        override fun observeRequests(userId: String): Flow<List<ProductRequestEntity>> =
            items.map { requests -> requests.filter { it.userId == userId } }
        override suspend fun getRequest(id: String): ProductRequestEntity? =
            items.value.firstOrNull { it.id == id }
        override suspend fun pendingRequests(userId: String, synced: String): List<ProductRequestEntity> =
            items.value.filter { it.userId == userId && it.syncStatus != synced }
        override suspend fun upsert(request: ProductRequestEntity) {
            items.value = items.value.filterNot { it.id == request.id } + request
        }
        override suspend fun upsertAll(requests: List<ProductRequestEntity>) {
            val byId = items.value.associateBy { it.id }.toMutableMap()
            requests.forEach { byId[it.id] = it }
            items.value = byId.values.toList()
        }
    }

    private class InMemoryHarvestListingDao : HarvestListingDao {
        val items = MutableStateFlow<List<HarvestListingEntity>>(emptyList())

        override fun observeForFarm(farmId: String): Flow<List<HarvestListingEntity>> =
            items.map { listings -> listings.filter { it.farmId == farmId } }
        override fun observeForOfficer(officerUid: String): Flow<List<HarvestListingEntity>> =
            items.map { listings -> listings.filter { it.officerUid == officerUid } }
        override suspend fun getListing(id: String): HarvestListingEntity? =
            items.value.firstOrNull { it.id == id }
        override suspend fun pendingListings(officerUid: String, synced: String): List<HarvestListingEntity> =
            items.value.filter { it.officerUid == officerUid && it.syncStatus != synced }
        override suspend fun findByRecommendationId(recommendationId: String): HarvestListingEntity? =
            items.value.firstOrNull { it.sourceRecommendationId == recommendationId }
        override suspend fun upsert(listing: HarvestListingEntity) {
            items.value = items.value.filterNot { it.id == listing.id } + listing
        }
        override suspend fun upsertAll(listings: List<HarvestListingEntity>) {
            val byId = items.value.associateBy { it.id }.toMutableMap()
            listings.forEach { byId[it.id] = it }
            items.value = byId.values.toList()
        }
    }

    private class InMemoryHarvestRequestDao : HarvestRequestDao {
        val items = MutableStateFlow<List<HarvestRequestEntity>>(emptyList())

        override fun observeForFarm(farmId: String): Flow<List<HarvestRequestEntity>> =
            items.map { requests -> requests.filter { it.farmId == farmId } }
        override fun observeForOfficerPath(farmPathPrefix: String): Flow<List<HarvestRequestEntity>> =
            items.map { requests ->
                val prefix = farmPathPrefix.removeSuffix("%")
                requests.filter { it.farmPath.startsWith(prefix) }
            }
        override suspend fun getRequest(id: String): HarvestRequestEntity? =
            items.value.firstOrNull { it.id == id }
        override suspend fun pendingRequests(synced: String): List<HarvestRequestEntity> =
            items.value.filter { it.syncStatus != synced }
        override suspend fun upsert(request: HarvestRequestEntity) {
            items.value = items.value.filterNot { it.id == request.id } + request
        }
        override suspend fun upsertAll(requests: List<HarvestRequestEntity>) {
            val byId = items.value.associateBy { it.id }.toMutableMap()
            requests.forEach { byId[it.id] = it }
            items.value = byId.values.toList()
        }
    }

    private class InMemorySensorReadingDao : SensorReadingDao {
        val items = MutableStateFlow<List<SensorReadingEntity>>(emptyList())

        override fun observeLatestForFarm(farmId: String): Flow<SensorReadingEntity?> =
            items.map { readings -> readings.filter { it.farmId == farmId }.maxByOrNull { it.recordedAt } }
        override suspend fun getLatestForFarm(farmId: String): SensorReadingEntity? =
            items.value.filter { it.farmId == farmId }.maxByOrNull { it.recordedAt }
        override fun observeRecentForFarm(farmId: String, limit: Int): Flow<List<SensorReadingEntity>> =
            items.map { readings -> readings.filter { it.farmId == farmId }.sortedByDescending { it.recordedAt }.take(limit) }
        override suspend fun getReading(id: String): SensorReadingEntity? = items.value.firstOrNull { it.id == id }
        override suspend fun pendingReadings(userId: String, synced: String): List<SensorReadingEntity> =
            items.value.filter { it.userId == userId && it.syncStatus != synced }
        override suspend fun readingsForUser(userId: String): List<SensorReadingEntity> =
            items.value.filter { it.userId == userId }.sortedByDescending { it.recordedAt }
        override suspend fun upsert(reading: SensorReadingEntity) {
            items.value = items.value.filterNot { it.id == reading.id } + reading
        }
    }

    private class InMemoryFarmRecommendationDao : FarmRecommendationDao {
        val items = MutableStateFlow<List<FarmRecommendationEntity>>(emptyList())

        override fun observeForFarm(farmId: String): Flow<List<FarmRecommendationEntity>> =
            items.map { recommendations -> recommendations.filter { it.farmId == farmId } }

        override suspend fun pendingRecommendations(userId: String, synced: String): List<FarmRecommendationEntity> =
            items.value.filter { it.userId == userId && it.syncStatus != synced }

        override suspend fun deleteForFarm(userId: String, farmId: String) {
            items.value = items.value.filterNot { it.userId == userId && it.farmId == farmId }
        }

        override suspend fun getRecommendation(id: String): FarmRecommendationEntity? =
            items.value.firstOrNull { it.id == id }

        override suspend fun upsertAll(recommendations: List<FarmRecommendationEntity>) {
            val ids = recommendations.map { it.id }.toSet()
            items.value = items.value.filterNot { it.id in ids } + recommendations
        }
    }
}