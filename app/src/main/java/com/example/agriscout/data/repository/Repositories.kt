package com.example.agriscout.data.repository

import com.example.agriscout.auth.FirebaseAuthService
import com.example.agriscout.auth.OfficerSession
import com.example.agriscout.data.local.DiseaseCatalogDao
import com.example.agriscout.data.local.DiseaseCatalogEntity
import com.example.agriscout.data.local.FarmDao
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.FarmRecommendationDao
import com.example.agriscout.data.local.FarmVisitDao
import com.example.agriscout.data.local.FieldReportDao
import com.example.agriscout.data.local.FieldReportEntity
import com.example.agriscout.data.local.HarvestListingDao
import com.example.agriscout.data.local.HarvestRequestDao
import com.example.agriscout.data.local.InventoryRequestDao
import com.example.agriscout.data.local.ProductRequestDao
import com.example.agriscout.data.local.SensorReadingDao
import com.example.agriscout.data.local.SyncStatus
import com.example.agriscout.data.local.WeatherWarningDao
import com.example.agriscout.data.local.WeatherWarningEntity
import com.example.agriscout.data.remote.CatalogRemoteService
import com.example.agriscout.data.remote.FarmCameraCapture
import com.example.agriscout.data.remote.FarmIoTModuleStatus
import com.example.agriscout.data.remote.SyncRemoteService
import com.example.agriscout.detection.DetectionResult
import com.example.agriscout.sync.ConnectivityChecker
import com.example.agriscout.sync.SyncConflictPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class AuthRepository(private val authService: FirebaseAuthService) {
    val currentUserId: String?
        get() = authService.currentUserId

    val currentSession: OfficerSession?
        get() = authService.currentSession

    val isConfigured: Boolean
        get() = authService.isConfigured

    fun isLoggedIn(): Boolean = authService.isLoggedIn()

    suspend fun login(email: String, password: String) {
        authService.login(email, password)
    }

    suspend fun register(email: String, password: String) {
        authService.register(email, password)
    }

    fun logout() {
        authService.logout()
    }

    suspend fun fetchOfficerAccess() = authService.fetchOfficerAccess()

    fun requireCurrentUserId(): String {
        return currentUserId ?: error("Login required. Please sign in with Firebase before saving or syncing officer data.")
    }

    companion object {
        const val LOCAL_USER_ID = "local-officer"
    }
}

class FarmRepository(
    private val farmDao: FarmDao,
    private val authService: FirebaseAuthService,
    private val remoteService: SyncRemoteService
) {
    fun observeFarms(userId: String): Flow<List<FarmEntity>> = farmDao.observeFarms(userId)

    suspend fun claimLegacyLocalData(userId: String) {
        farmDao.claimLegacyFarms(
            legacyUserId = AuthRepository.LOCAL_USER_ID,
            newUserId = userId,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun saveFarm(
        existing: FarmEntity?,
        userId: String,
        farmName: String,
        farmerName: String,
        cropType: String,
        locationText: String,
        landSize: String,
        notes: String,
        latitude: Double? = existing?.latitude,
        longitude: Double? = existing?.longitude,
        gpsAccuracyMeters: Double? = existing?.gpsAccuracyMeters,
        gpsCapturedAt: Long? = existing?.gpsCapturedAt,
        gpsSource: String? = existing?.gpsSource,
        plantingDate: Long? = existing?.plantingDate,
        assignedDeviceId: String? = existing?.assignedDeviceId,
        assignedCameraDeviceId: String? = existing?.assignedCameraDeviceId,
        photoLocalUri: String? = existing?.photoLocalUri,
        remotePhotoUrl: String? = existing?.remotePhotoUrl
    ) {
        val now = System.currentTimeMillis()
        val photoChanged = photoLocalUri != existing?.photoLocalUri
        farmDao.upsert(
            FarmEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                userId = userId,
                ownerUserId = existing?.ownerUserId ?: userId,
                farmName = farmName.trim(),
                farmerName = farmerName.trim(),
                cropType = cropType.trim(),
                locationText = locationText.trim(),
                landSize = landSize.trim(),
                notes = notes.trim(),
                latitude = latitude,
                longitude = longitude,
                gpsAccuracyMeters = gpsAccuracyMeters,
                gpsCapturedAt = gpsCapturedAt,
                gpsSource = gpsSource,
                plantingDate = plantingDate,
                assignedDeviceId = assignedDeviceId,
                assignedCameraDeviceId = assignedCameraDeviceId,
                photoLocalUri = photoLocalUri,
                remotePhotoUrl = if (photoChanged) null else remotePhotoUrl,
                syncStatus = SyncStatus.PENDING,
                remoteId = existing?.remoteId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteFarm(farm: FarmEntity): Boolean {
        val userId = authService.currentUserId
        val remoteId = farm.remoteId ?: farm.id.takeIf { farm.syncStatus == SyncStatus.SYNCED }
        val remoteDeleted = if (userId != null && remoteId != null) {
            runCatching { remoteService.deleteFarmAndReports(userId, remoteId) }.isSuccess
        } else {
            true
        }
        farmDao.delete(farm)
        return remoteDeleted
    }
}

class ReportRepository(
    private val reportDao: FieldReportDao,
    private val authService: FirebaseAuthService,
    private val remoteService: SyncRemoteService
) {
    fun observeReports(userId: String): Flow<List<FieldReportEntity>> = reportDao.observeReports(userId)

    suspend fun claimLegacyLocalData(userId: String) {
        reportDao.claimLegacyReports(
            legacyUserId = AuthRepository.LOCAL_USER_ID,
            newUserId = userId,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun saveReport(
        existing: FieldReportEntity?,
        userId: String,
        farmId: String,
        cropType: String,
        symptoms: String,
        severity: String,
        estimatedYield: String,
        notes: String,
        pestObservations: String = "",
        growthStage: String = "",
        cropConditionDetail: String = "",
        recommendedActions: String = "",
        followUpNotes: String = "",
        latitude: Double?,
        longitude: Double?,
        gpsAccuracyMeters: Double? = null,
        gpsCapturedAt: Long? = null,
        gpsSource: String? = null,
        imageLocalUri: String?,
        detectionResult: DetectionResult? = null,
        preventiveMeasures: String? = null
    ) {
        val now = System.currentTimeMillis()
        reportDao.upsert(
            FieldReportEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                farmId = farmId,
                userId = userId,
                cropType = cropType.trim(),
                symptoms = symptoms.trim(),
                severity = severity,
                estimatedYield = estimatedYield.trim(),
                notes = notes.trim(),
                pestObservations = pestObservations.trim(),
                growthStage = growthStage.trim(),
                cropConditionDetail = cropConditionDetail.trim(),
                recommendedActions = recommendedActions.trim(),
                followUpNotes = followUpNotes.trim(),
                latitude = latitude,
                longitude = longitude,
                gpsAccuracyMeters = gpsAccuracyMeters,
                gpsCapturedAt = gpsCapturedAt,
                gpsSource = gpsSource,
                imageLocalUri = imageLocalUri,
                remoteImageUrl = if (imageLocalUri == existing?.imageLocalUri) existing?.remoteImageUrl else null,
                issueType = detectionResult?.issueType ?: existing?.issueType,
                detectedIssue = detectionResult?.issueName ?: existing?.detectedIssue,
                detectionConfidence = detectionResult?.confidence ?: existing?.detectionConfidence,
                matchedRuleId = detectionResult?.matchedRuleId ?: existing?.matchedRuleId,
                recommendation = detectionResult?.recommendation ?: existing?.recommendation,
                preventiveMeasures = preventiveMeasures ?: detectionResult?.prevention ?: existing?.preventiveMeasures,
                detectionExplanation = detectionResult?.explanation?.takeIf { it.isNotBlank() }
                    ?: existing?.detectionExplanation,
                detectionSource = detectionResult?.analysisSource ?: existing?.detectionSource,
                detectionUpdatedAt = if (detectionResult != null) now else existing?.detectionUpdatedAt,
                syncStatus = SyncStatus.PENDING,
                remoteId = existing?.remoteId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    suspend fun deleteReport(report: FieldReportEntity): Boolean {
        val userId = authService.currentUserId
        val remoteId = report.remoteId ?: report.id.takeIf { report.syncStatus == SyncStatus.SYNCED }
        val remoteDeleted = if (userId != null && remoteId != null) {
            runCatching { remoteService.deleteReport(userId, remoteId) }.isSuccess
        } else {
            true
        }
        reportDao.delete(report)
        return remoteDeleted
    }
}

class CatalogRepository(
    private val diseaseCatalogDao: DiseaseCatalogDao,
    private val weatherWarningDao: WeatherWarningDao,
    private val remoteService: CatalogRemoteService
) {
    fun observeCatalog(): Flow<List<DiseaseCatalogEntity>> = diseaseCatalogDao.observeCatalog()
    fun observeWarnings(): Flow<List<WeatherWarningEntity>> = weatherWarningDao.observeWarnings()

    suspend fun refreshRemoteContent() {
        diseaseCatalogDao.upsertAll(remoteService.fetchDiseaseCatalog())
        weatherWarningDao.upsertAll(remoteService.fetchWeatherWarnings())
    }
}

class SyncRepository(
    private val farmDao: FarmDao,
    private val reportDao: FieldReportDao,
    private val farmVisitDao: FarmVisitDao,
    private val farmRecommendationDao: FarmRecommendationDao,
    private val inventoryRequestDao: InventoryRequestDao,
    private val productRequestDao: ProductRequestDao,
    private val harvestListingDao: HarvestListingDao,
    private val harvestRequestDao: HarvestRequestDao,
    private val sensorReadingDao: SensorReadingDao,
    private val authService: FirebaseAuthService,
    private val remoteService: SyncRemoteService,
    private val connectivityChecker: ConnectivityChecker = ConnectivityChecker { true },
    private val currentUserIdProvider: () -> String? = { authService.currentUserId }
) {
    suspend fun getSyncStatusCounts(): SyncStatusCounts {
        val userId = currentUserIdProvider() ?: return SyncStatusCounts()
        val farms = farmDao.observeFarms(userId).firstOrNull().orEmpty()
        val reports = reportDao.observeReports(userId).firstOrNull().orEmpty()
        val visits = farmVisitDao.observeVisits(userId).firstOrNull().orEmpty()
        val requests = inventoryRequestDao.observeRequests(userId).firstOrNull().orEmpty()
        val readings = sensorReadingDao.readingsForUser(userId)
        return SyncStatusCounts(
            pendingFarms = farms.count { it.syncStatus == SyncStatus.PENDING },
            pendingReports = reports.count { it.syncStatus == SyncStatus.PENDING },
            pendingVisits = visits.count { it.syncStatus == SyncStatus.PENDING },
            pendingInventoryRequests = requests.count { it.syncStatus == SyncStatus.PENDING },
            pendingSensorReadings = readings.count { it.syncStatus == SyncStatus.PENDING },
            syncedFarms = farms.count { it.syncStatus == SyncStatus.SYNCED },
            syncedReports = reports.count { it.syncStatus == SyncStatus.SYNCED },
            syncedVisits = visits.count { it.syncStatus == SyncStatus.SYNCED },
            syncedInventoryRequests = requests.count { it.syncStatus == SyncStatus.SYNCED },
            syncedSensorReadings = readings.count { it.syncStatus == SyncStatus.SYNCED },
            failedFarms = farms.count { it.syncStatus == SyncStatus.FAILED },
            failedReports = reports.count { it.syncStatus == SyncStatus.FAILED },
            failedVisits = visits.count { it.syncStatus == SyncStatus.FAILED },
            failedInventoryRequests = requests.count { it.syncStatus == SyncStatus.FAILED },
            failedSensorReadings = readings.count { it.syncStatus == SyncStatus.FAILED }
        )
    }

    suspend fun loadFarmIoTBundle(farmId: String): FarmIoTBundle {
        val userId = currentUserIdProvider() ?: return FarmIoTBundle()
        val modules = runCatching { remoteService.fetchOfficerIoTDevices(userId) }
            .getOrDefault(emptyList())
            .filter { it.farmId == farmId || it.farmId.isNullOrBlank() }
        val captures = runCatching {
            remoteService.fetchCameraCapturesForFarm(userId, farmId)
        }.getOrDefault(emptyList())
        return FarmIoTBundle(
            sensor = modules.firstOrNull {
                it.deviceType.equals("sensor", ignoreCase = true) && it.farmId == farmId
            } ?: modules.firstOrNull { it.deviceType.equals("sensor", ignoreCase = true) },
            camera = modules.firstOrNull {
                it.deviceType.equals("camera", ignoreCase = true) && it.farmId == farmId
            } ?: modules.firstOrNull { it.deviceType.equals("camera", ignoreCase = true) },
            captures = captures.filter { it.farmId == farmId }
        )
    }

    suspend fun syncPendingData(): SyncResult {
        val beforeCounts = getSyncStatusCounts()
        val userId = currentUserIdProvider() ?: return SyncResult(
            farmsSynced = 0,
            reportsSynced = 0,
            visitsSynced = 0,
            inventoryRequestsSynced = 0,
            sensorReadingsSynced = 0,
            statusCounts = beforeCounts,
            requiresAuthentication = true,
            errors = listOf("Login required for Firebase sync."),
            message = "Login required for Firebase sync."
        )

        if (!connectivityChecker.isOnline()) {
            return SyncResult(
                farmsSynced = 0,
                reportsSynced = 0,
                visitsSynced = 0,
                inventoryRequestsSynced = 0,
                sensorReadingsSynced = 0,
                statusCounts = beforeCounts,
                waitingForConnectivity = true,
                errors = listOf("Waiting for connectivity. Pending data remains stored on this device."),
                message = "Waiting for connectivity. Pending data remains stored on this device."
            )
        }

        var farmsSynced = 0
        var reportsSynced = 0
        var visitsSynced = 0
        var inventoryRequestsSynced = 0
        var sensorReadingsSynced = 0
        var farmsRestored = 0
        var reportsRestored = 0
        var visitsRestored = 0
        var inventoryRequestsRestored = 0
        var sensorReadingsRestored = 0
        var failures = 0
        var pausedForConnectivity = false
        val errors = mutableListOf<String>()

        suspend fun noteUploadFailure(label: String, throwable: Throwable, markFailed: suspend () -> Unit) {
            if (pausedForConnectivity) return
            if (SyncConflictPolicy.isConnectivityFailure(throwable) || !connectivityChecker.isOnline()) {
                pausedForConnectivity = true
                errors += "Sync paused: waiting for connectivity. Local data is safe and will retry."
                return
            }
            markFailed()
            failures++
            errors += "$label failed: ${throwable.localizedMessage ?: "Unknown error"}"
        }

        farmDao.pendingFarms(userId).forEach { farm ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val photoUrl = remoteService.uploadFarmPhotoIfNeeded(userId, farm)
                val remoteId = remoteService.uploadFarm(userId, farm, photoUrl)
                farmDao.upsert(
                    farm.copy(
                        syncStatus = SyncStatus.SYNCED,
                        remoteId = remoteId,
                        remotePhotoUrl = photoUrl
                    )
                )
                farmsSynced++
            }.onFailure { throwable ->
                noteUploadFailure("Farm '${farm.farmName}'", throwable) {
                    farmDao.upsert(farm.copy(syncStatus = SyncStatus.FAILED))
                }
            }
        }

        reportDao.pendingReports(userId).forEach { report ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val imageUrl = remoteService.uploadReportImageIfNeeded(userId, report)
                val remoteId = remoteService.uploadReport(userId, report, imageUrl)
                reportDao.upsert(report.copy(syncStatus = SyncStatus.SYNCED, remoteId = remoteId, remoteImageUrl = imageUrl))
                reportsSynced++
            }.onFailure { throwable ->
                val reportLabel = report.cropType.ifBlank { report.id }
                noteUploadFailure("Report '$reportLabel'", throwable) {
                    reportDao.upsert(report.copy(syncStatus = SyncStatus.FAILED))
                }
            }
        }

        farmVisitDao.pendingVisits(userId).forEach { visit ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val photoUrl = remoteService.uploadFarmVisitPhotoIfNeeded(userId, visit)
                val remoteId = remoteService.uploadFarmVisit(userId, visit, photoUrl)
                farmVisitDao.upsert(
                    visit.copy(
                        syncStatus = SyncStatus.SYNCED,
                        remoteId = remoteId,
                        remotePhotoUrl = photoUrl
                    )
                )
                visitsSynced++
            }.onFailure { throwable ->
                noteUploadFailure("Visit '${visit.cropCondition.ifBlank { visit.id }}'", throwable) {
                    farmVisitDao.upsert(visit.copy(syncStatus = SyncStatus.FAILED))
                }
            }
        }

        inventoryRequestDao.pendingRequests(userId).forEach { request ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val remoteId = remoteService.uploadInventoryRequest(userId, request)
                inventoryRequestDao.upsert(request.copy(syncStatus = SyncStatus.SYNCED, remoteId = remoteId))
                inventoryRequestsSynced++
            }.onFailure { throwable ->
                noteUploadFailure("Inventory request '${request.itemType.ifBlank { request.id }}'", throwable) {
                    inventoryRequestDao.upsert(request.copy(syncStatus = SyncStatus.FAILED))
                }
            }
        }

        productRequestDao.pendingRequests(userId).forEach { request ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val remoteId = request.remoteId ?: request.id
                val cancelled = request.status.equals("cancelled", ignoreCase = true)
                when {
                    cancelled && request.remoteId == null -> {
                        // Never reached Firestore; keep local cancel without a create write.
                        productRequestDao.upsert(
                            request.copy(syncStatus = SyncStatus.SYNCED, remoteId = null)
                        )
                    }
                    cancelled -> {
                        remoteService.updateProductRequestStatus(
                            requestId = remoteId,
                            status = request.status,
                            updatedAt = request.updatedAt
                        )
                        productRequestDao.upsert(
                            request.copy(syncStatus = SyncStatus.SYNCED, remoteId = remoteId)
                        )
                    }
                    else -> {
                        val uploadedId = remoteService.uploadProductRequest(userId, request)
                        productRequestDao.upsert(
                            request.copy(syncStatus = SyncStatus.SYNCED, remoteId = uploadedId)
                        )
                    }
                }
            }.onFailure { throwable ->
                noteUploadFailure("Product request '${request.productName.ifBlank { request.id }}'", throwable) {
                    productRequestDao.upsert(request.copy(syncStatus = SyncStatus.FAILED))
                }
            }
        }

        harvestListingDao.pendingListings(userId).forEach { listing ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val remoteId = remoteService.uploadHarvestListing(listing)
                harvestListingDao.upsert(
                    listing.copy(syncStatus = SyncStatus.SYNCED, remoteId = remoteId)
                )
            }.onFailure { throwable ->
                noteUploadFailure("Harvest listing '${listing.cropType} / ${listing.farmName}'", throwable) {
                    harvestListingDao.upsert(listing.copy(syncStatus = SyncStatus.FAILED))
                }
            }
        }

        harvestRequestDao.pendingRequests().forEach { request ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val remoteId = request.remoteId ?: request.id
                remoteService.updateHarvestRequestResponse(
                    requestId = remoteId,
                    status = request.status,
                    officerNote = request.officerNote,
                    updatedAt = request.updatedAt
                )
                harvestRequestDao.upsert(
                    request.copy(syncStatus = SyncStatus.SYNCED, remoteId = remoteId)
                )
            }.onFailure { throwable ->
                noteUploadFailure(
                    "Harvest request '${request.buyerName.ifBlank { request.id }}'",
                    throwable
                ) {
                    harvestRequestDao.upsert(request.copy(syncStatus = SyncStatus.FAILED))
                }
            }
        }

        sensorReadingDao.pendingReadings(userId).forEach { reading ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val remoteId = remoteService.uploadSensorReading(userId, reading)
                sensorReadingDao.upsert(reading.copy(syncStatus = SyncStatus.SYNCED, remoteId = remoteId))
                sensorReadingsSynced++
            }.onFailure { throwable ->
                noteUploadFailure("Sensor reading '${reading.id}'", throwable) {
                    sensorReadingDao.upsert(reading.copy(syncStatus = SyncStatus.FAILED))
                }
            }
        }

        farmRecommendationDao.pendingRecommendations(userId).forEach { recommendation ->
            if (pausedForConnectivity) return@forEach
            runCatching {
                val remoteId = remoteService.uploadRecommendation(userId, recommendation)
                farmRecommendationDao.upsertAll(
                    listOf(
                        recommendation.copy(
                            syncStatus = SyncStatus.SYNCED,
                            remoteId = remoteId
                        )
                    )
                )
            }.onFailure { throwable ->
                noteUploadFailure("Recommendation '${recommendation.title}'", throwable) {
                    farmRecommendationDao.upsertAll(
                        listOf(recommendation.copy(syncStatus = SyncStatus.FAILED))
                    )
                }
            }
        }

        if (!pausedForConnectivity && connectivityChecker.isOnline()) {
            val localFarmsByRemoteId = farmDao.observeFarms(userId)
                .firstOrNull()
                .orEmpty()
                .filter { !it.remoteId.isNullOrBlank() }
                .associateBy { it.remoteId!! }
            val localVisitsByRemoteId = farmVisitDao.observeVisits(userId)
                .firstOrNull()
                .orEmpty()
                .filter { !it.remoteId.isNullOrBlank() }
                .associateBy { it.remoteId!! }
            val localRequestsByRemoteId = inventoryRequestDao.observeRequests(userId)
                .firstOrNull()
                .orEmpty()
                .filter { !it.remoteId.isNullOrBlank() }
                .associateBy { it.remoteId!! }
            val localReadingsByRemoteId = sensorReadingDao.readingsForUser(userId)
                .filter { !it.remoteId.isNullOrBlank() }
                .associateBy { it.remoteId!! }
            val localReportsByRemoteId = reportDao.observeReports(userId)
                .firstOrNull()
                .orEmpty()
                .filter { !it.remoteId.isNullOrBlank() }
                .associateBy { it.remoteId!! }

            remoteService.fetchFarms(userId).forEach { remoteFarm ->
                val localFarm = farmDao.getFarm(remoteFarm.id) ?: localFarmsByRemoteId[remoteFarm.id]
                if (
                    SyncConflictPolicy.shouldApplyRemote(
                        localSyncStatus = localFarm?.syncStatus,
                        localUpdatedAt = localFarm?.updatedAt ?: 0L,
                        remoteUpdatedAt = remoteFarm.updatedAt
                    )
                ) {
                    farmDao.upsert(
                        remoteFarm.copy(
                            photoLocalUri = localFarm?.photoLocalUri,
                            syncStatus = SyncStatus.SYNCED,
                            remoteId = remoteFarm.remoteId ?: remoteFarm.id
                        )
                    )
                    farmsRestored++
                }
            }

            remoteService.fetchReports(userId).forEach { remoteReport ->
                val localFarm = farmDao.getFarm(remoteReport.farmId)
                val localReport = reportDao.getReport(remoteReport.id) ?: localReportsByRemoteId[remoteReport.id]
                if (
                    localFarm != null &&
                    SyncConflictPolicy.shouldApplyRemote(
                        localSyncStatus = localReport?.syncStatus,
                        localUpdatedAt = localReport?.updatedAt ?: 0L,
                        remoteUpdatedAt = remoteReport.updatedAt
                    )
                ) {
                    reportDao.upsert(
                        remoteReport.copy(
                            imageLocalUri = localReport?.imageLocalUri,
                            syncStatus = SyncStatus.SYNCED,
                            remoteId = remoteReport.remoteId ?: remoteReport.id
                        )
                    )
                    reportsRestored++
                }
            }

            remoteService.fetchFarmVisits(userId).forEach { remoteVisit ->
                val localFarm = farmDao.getFarm(remoteVisit.farmId)
                val localVisit = farmVisitDao.getVisit(remoteVisit.id) ?: localVisitsByRemoteId[remoteVisit.id]
                if (
                    localFarm != null &&
                    SyncConflictPolicy.shouldApplyRemote(
                        localSyncStatus = localVisit?.syncStatus,
                        localUpdatedAt = localVisit?.updatedAt ?: 0L,
                        remoteUpdatedAt = remoteVisit.updatedAt
                    )
                ) {
                    farmVisitDao.upsert(
                        remoteVisit.copy(
                            photoLocalUri = localVisit?.photoLocalUri,
                            syncStatus = SyncStatus.SYNCED,
                            remoteId = remoteVisit.remoteId ?: remoteVisit.id
                        )
                    )
                    visitsRestored++
                }
            }

            remoteService.fetchInventoryRequests(userId).forEach { remoteRequest ->
                val hasValidFarm = remoteRequest.farmId.isNullOrBlank() || farmDao.getFarm(remoteRequest.farmId) != null
                val localRequest = inventoryRequestDao.getRequest(remoteRequest.id) ?: localRequestsByRemoteId[remoteRequest.id]
                if (
                    hasValidFarm &&
                    SyncConflictPolicy.shouldApplyRemote(
                        localSyncStatus = localRequest?.syncStatus,
                        localUpdatedAt = localRequest?.updatedAt ?: 0L,
                        remoteUpdatedAt = remoteRequest.updatedAt
                    )
                ) {
                    inventoryRequestDao.upsert(
                        remoteRequest.copy(
                            syncStatus = SyncStatus.SYNCED,
                            remoteId = remoteRequest.remoteId ?: remoteRequest.id
                        )
                    )
                    inventoryRequestsRestored++
                }
            }

            remoteService.fetchProductRequests(userId).forEach { remoteRequest ->
                val localRequest = productRequestDao.getRequest(remoteRequest.id)
                if (
                    SyncConflictPolicy.shouldApplyRemote(
                        localSyncStatus = localRequest?.syncStatus,
                        localUpdatedAt = localRequest?.updatedAt ?: 0L,
                        remoteUpdatedAt = remoteRequest.updatedAt
                    )
                ) {
                    productRequestDao.upsert(
                        remoteRequest.copy(
                            syncStatus = SyncStatus.SYNCED,
                            remoteId = remoteRequest.remoteId ?: remoteRequest.id
                        )
                    )
                }
            }

            remoteService.fetchSensorReadings(userId).forEach { remoteReading ->
                val localFarm = farmDao.getFarm(remoteReading.farmId)
                val localReading = sensorReadingDao.getReading(remoteReading.id) ?: localReadingsByRemoteId[remoteReading.id]
                if (
                    localFarm != null &&
                    SyncConflictPolicy.shouldApplyRemote(
                        localSyncStatus = localReading?.syncStatus,
                        localUpdatedAt = localReading?.updatedAt ?: 0L,
                        remoteUpdatedAt = remoteReading.updatedAt
                    )
                ) {
                    sensorReadingDao.upsert(
                        remoteReading.copy(
                            syncStatus = SyncStatus.SYNCED,
                            remoteId = remoteReading.remoteId ?: remoteReading.id
                        )
                    )
                    sensorReadingsRestored++
                }
            }

            remoteService.fetchRecommendations(userId).forEach { remoteRecommendation ->
                val localFarm = farmDao.getFarm(remoteRecommendation.farmId)
                val localRecommendation = farmRecommendationDao.getRecommendation(remoteRecommendation.id)
                if (
                    localFarm != null &&
                    SyncConflictPolicy.shouldApplyRemote(
                        localSyncStatus = localRecommendation?.syncStatus,
                        localUpdatedAt = localRecommendation?.updatedAt ?: 0L,
                        remoteUpdatedAt = remoteRecommendation.updatedAt
                    )
                ) {
                    farmRecommendationDao.upsertAll(
                        listOf(
                            remoteRecommendation.copy(
                                syncStatus = SyncStatus.SYNCED,
                                remoteId = remoteRecommendation.remoteId ?: remoteRecommendation.id
                            )
                        )
                    )
                }
            }
        }

        val statusCounts = getSyncStatusCounts()
        return SyncResult(
            farmsSynced = farmsSynced,
            reportsSynced = reportsSynced,
            visitsSynced = visitsSynced,
            inventoryRequestsSynced = inventoryRequestsSynced,
            sensorReadingsSynced = sensorReadingsSynced,
            farmsRestored = farmsRestored,
            reportsRestored = reportsRestored,
            visitsRestored = visitsRestored,
            inventoryRequestsRestored = inventoryRequestsRestored,
            sensorReadingsRestored = sensorReadingsRestored,
            failures = failures,
            statusCounts = statusCounts,
            waitingForConnectivity = pausedForConnectivity,
            errors = errors,
            message = buildSyncMessage(
                farmsSynced = farmsSynced,
                reportsSynced = reportsSynced,
                visitsSynced = visitsSynced,
                inventoryRequestsSynced = inventoryRequestsSynced,
                sensorReadingsSynced = sensorReadingsSynced,
                farmsRestored = farmsRestored,
                reportsRestored = reportsRestored,
                visitsRestored = visitsRestored,
                inventoryRequestsRestored = inventoryRequestsRestored,
                sensorReadingsRestored = sensorReadingsRestored,
                failures = failures,
                waitingForConnectivity = pausedForConnectivity
            )
        )
    }
}

data class SyncStatusCounts(
    val pendingFarms: Int = 0,
    val pendingReports: Int = 0,
    val pendingVisits: Int = 0,
    val pendingInventoryRequests: Int = 0,
    val pendingSensorReadings: Int = 0,
    val syncedFarms: Int = 0,
    val syncedReports: Int = 0,
    val syncedVisits: Int = 0,
    val syncedInventoryRequests: Int = 0,
    val syncedSensorReadings: Int = 0,
    val failedFarms: Int = 0,
    val failedReports: Int = 0,
    val failedVisits: Int = 0,
    val failedInventoryRequests: Int = 0,
    val failedSensorReadings: Int = 0
)

data class FarmIoTBundle(
    val sensor: FarmIoTModuleStatus? = null,
    val camera: FarmIoTModuleStatus? = null,
    val captures: List<FarmCameraCapture> = emptyList()
)

data class SyncResult(
    val farmsSynced: Int,
    val reportsSynced: Int,
    val visitsSynced: Int = 0,
    val inventoryRequestsSynced: Int = 0,
    val sensorReadingsSynced: Int = 0,
    val farmsRestored: Int = 0,
    val reportsRestored: Int = 0,
    val visitsRestored: Int = 0,
    val inventoryRequestsRestored: Int = 0,
    val sensorReadingsRestored: Int = 0,
    val failures: Int = 0,
    val statusCounts: SyncStatusCounts = SyncStatusCounts(),
    val requiresAuthentication: Boolean = false,
    val waitingForConnectivity: Boolean = false,
    val errors: List<String> = emptyList(),
    val message: String
)

private fun buildSyncMessage(
    farmsSynced: Int,
    reportsSynced: Int,
    visitsSynced: Int,
    inventoryRequestsSynced: Int,
    sensorReadingsSynced: Int,
    farmsRestored: Int,
    reportsRestored: Int,
    visitsRestored: Int,
    inventoryRequestsRestored: Int,
    sensorReadingsRestored: Int,
    failures: Int,
    waitingForConnectivity: Boolean = false
): String {
    if (waitingForConnectivity) {
        val uploaded = farmsSynced + reportsSynced + visitsSynced + inventoryRequestsSynced + sensorReadingsSynced
        return if (uploaded > 0) {
            "Sync paused after uploading $uploaded item${plural(uploaded)}: waiting for connectivity. Remaining items stay local for retry."
        } else {
            "Waiting for connectivity. Pending data remains stored on this device."
        }
    }
    val completed = farmsSynced + reportsSynced + visitsSynced + inventoryRequestsSynced + sensorReadingsSynced +
        farmsRestored + reportsRestored + visitsRestored + inventoryRequestsRestored + sensorReadingsRestored
    val parts = mutableListOf<String>()
    if (farmsSynced > 0) parts += "$farmsSynced farm${plural(farmsSynced)} uploaded"
    if (reportsSynced > 0) parts += "$reportsSynced report${plural(reportsSynced)} uploaded"
    if (visitsSynced > 0) parts += "$visitsSynced visit${plural(visitsSynced)} uploaded"
    if (inventoryRequestsSynced > 0) parts += "$inventoryRequestsSynced inventory request${plural(inventoryRequestsSynced)} uploaded"
    if (sensorReadingsSynced > 0) parts += "$sensorReadingsSynced sensor reading${plural(sensorReadingsSynced)} uploaded"
    if (farmsRestored > 0) parts += "$farmsRestored farm${plural(farmsRestored)} restored"
    if (reportsRestored > 0) parts += "$reportsRestored report${plural(reportsRestored)} restored"
    if (visitsRestored > 0) parts += "$visitsRestored visit${plural(visitsRestored)} restored"
    if (inventoryRequestsRestored > 0) parts += "$inventoryRequestsRestored inventory request${plural(inventoryRequestsRestored)} restored"
    if (sensorReadingsRestored > 0) parts += "$sensorReadingsRestored sensor reading${plural(sensorReadingsRestored)} restored"
    if (failures > 0) parts += "$failures item${plural(failures)} failed and remain retryable"
    return when {
        parts.isNotEmpty() -> "Sync complete: ${parts.joinToString(", ")}."
        completed == 0 -> "Sync complete: no pending or cloud changes."
        else -> "Sync complete."
    }
}

private fun plural(count: Int): String = if (count == 1) "" else "s"
