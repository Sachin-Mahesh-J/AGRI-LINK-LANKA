package com.example.agriscout.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.agriscout.auth.OfficerAccessStatus
import com.example.agriscout.auth.OfficerRoles
import com.example.agriscout.data.local.DiseaseCatalogEntity
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.FarmVisitEntity
import com.example.agriscout.data.local.FieldReportEntity
import com.example.agriscout.data.local.InventoryItemEntity
import com.example.agriscout.data.local.InventoryRequestEntity
import com.example.agriscout.data.local.ProductRequestEntity
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.SupplierProductEntity
import com.example.agriscout.data.local.SyncStatus
import com.example.agriscout.data.local.WeatherSnapshotEntity
import com.example.agriscout.data.local.WeatherWarningEntity
import com.example.agriscout.data.repository.AuthRepository
import com.example.agriscout.data.repository.CatalogRepository
import com.example.agriscout.data.repository.DetectionRepository
import com.example.agriscout.data.repository.FarmRepository
import com.example.agriscout.data.repository.FarmVisitRepository
import com.example.agriscout.data.repository.InventoryRepository
import com.example.agriscout.data.repository.MarketplaceRepository
import com.example.agriscout.data.repository.NotificationRepository
import com.example.agriscout.data.repository.ReportRepository
import com.example.agriscout.data.repository.SensorRepository
import com.example.agriscout.data.repository.SyncResult
import com.example.agriscout.data.repository.SyncStatusCounts
import com.example.agriscout.data.repository.SyncRepository
import com.example.agriscout.data.repository.WeatherRepository
import com.example.agriscout.crop.CropLifecycleEstimate
import com.example.agriscout.crop.CropLifecycleEstimator
import com.example.agriscout.crop.CropStage
import com.example.agriscout.di.AppContainer
import com.example.agriscout.detection.DetectionResult
import com.example.agriscout.detection.IssueTypes
import com.example.agriscout.location.LocationService
import com.example.agriscout.marketplace.SupplierProductMatcher
import com.example.agriscout.marketplace.SupplierProductOffer
import com.example.agriscout.data.repository.RecommendationRepository
import com.example.agriscout.data.repository.toRecommendation
import com.example.agriscout.recommendation.Recommendation
import com.example.agriscout.sync.NetworkConnectivityMonitor
import com.example.agriscout.sync.SyncConflictPolicy
import com.example.agriscout.sync.SyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AuthFormState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val message: String? = null
)

data class FarmFormState(
    val id: String? = null,
    val farmName: String = "",
    val farmerName: String = "",
    val cropType: String = "",
    val locationText: String = "",
    val landSize: String = "",
    val notes: String = "",
    val plantingDateText: String = "",
    val photoLocalUri: String? = null,
    val remotePhotoUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Double? = null,
    val gpsCapturedAt: Long? = null,
    val gpsSource: String? = null,
    val locationLoading: Boolean = false,
    val saving: Boolean = false,
    val assignedDeviceId: String = "",
    val assignedCameraDeviceId: String = "",
    val message: String? = null
)

data class ReportFormState(
    val id: String? = null,
    val farmId: String = "",
    val cropType: String = "",
    val symptoms: String = "",
    val severity: String = "Low",
    val estimatedYield: String = "",
    val notes: String = "",
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
    val locationLoading: Boolean = false,
    val locationDenied: Boolean = false,
    val imageLocalUri: String? = null,
    val issueType: String? = null,
    val detectedIssue: String? = null,
    val detectionConfidence: Int? = null,
    val matchedRuleId: String? = null,
    val recommendation: String? = null,
    val preventiveMeasures: String? = null,
    val detectionExplanation: String? = null,
    val detectionSource: String? = null,
    val detectionUpdatedAt: Long? = null,
    val analyzing: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null
)

data class CrudOperationState(
    val deletingFarmId: String? = null,
    val deletingReportId: String? = null
)

data class DashboardState(
    val farmCount: Int = 0,
    val reportCount: Int = 0,
    val pendingFarmCount: Int = 0,
    val pendingReportCount: Int = 0,
    val failedFarmCount: Int = 0,
    val failedReportCount: Int = 0,
    val pendingInventoryCount: Int = 0
)

data class SensorDashboardState(
    val selectedFarmId: String? = null,
    val latestReading: SensorReadingEntity? = null,
    val recentReadings: List<SensorReadingEntity> = emptyList(),
    val captures: List<com.example.agriscout.data.remote.FarmCameraCapture> = emptyList(),
    val sensorModule: com.example.agriscout.data.remote.FarmIoTModuleStatus? = null,
    val cameraModule: com.example.agriscout.data.remote.FarmIoTModuleStatus? = null,
    val refreshing: Boolean = false,
    val isLiveData: Boolean = false,
    val isStale: Boolean = false,
    val dataSourceLabel: String = "Simulation",
    val message: String? = null
)

data class InventoryRequestFormState(
    val farmId: String = "",
    val itemType: String = "Fertilizers",
    val inventoryItemId: String? = null,
    val quantity: String = "",
    val reason: String = "",
    val submitting: Boolean = false,
    val message: String? = null
)

data class InventoryCatalogState(
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val lastRefreshedAt: Long? = null
)

data class FarmVisitFormState(
    val farmId: String = "",
    val cropCondition: String = "Good",
    val cropConditionDetail: String = "",
    val pestObservations: String = "",
    val growthStage: String = "",
    val recommendedActions: String = "",
    val followUpNotes: String = "",
    val notes: String = "",
    val photoLocalUri: String? = null,
    val remotePhotoUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Double? = null,
    val gpsCapturedAt: Long? = null,
    val gpsSource: String? = null,
    val locationLoading: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null
)

data class RecommendationState(
    val selectedFarmId: String? = null,
    val lifecycleEstimate: CropLifecycleEstimate = CropLifecycleEstimate(
        stage = CropStage.UNKNOWN,
        ageDays = null,
        summary = "Select a farm to view lifecycle and recommendations."
    ),
    val recommendations: List<Recommendation> = emptyList(),
    val supplierOffersByCategory: Map<String, List<SupplierProductOffer>> = emptyMap(),
    val productRequests: List<ProductRequestEntity> = emptyList(),
    val harvestListings: List<com.example.agriscout.data.local.HarvestListingEntity> = emptyList(),
    val harvestRequests: List<com.example.agriscout.data.local.HarvestRequestEntity> = emptyList()
)

data class OfficerProfileState(
    val isFirebaseConfigured: Boolean = true,
    val isLoggedIn: Boolean = false,
    val email: String? = null,
    val userId: String? = null
)

data class OfficerAccessState(
    val role: String? = null,
    val status: String? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val canOperate: Boolean = false,
    val canSync: Boolean = false,
    val title: String = "",
    val message: String = ""
)

data class SyncOverviewState(
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
    val failedSensorReadings: Int = 0,
    val syncing: Boolean = false,
    val isOnline: Boolean = true,
    val waitingForConnectivity: Boolean = false,
    val blockedByAccess: Boolean = false,
    val conflictPolicySummary: String = SyncConflictPolicy.SUMMARY,
    val lastResultSummary: String? = null,
    val lastResultErrors: List<String> = emptyList()
) {
    val pendingTotal: Int
        get() = pendingFarms + pendingReports + pendingVisits + pendingInventoryRequests + pendingSensorReadings

    val syncedTotal: Int
        get() = syncedFarms + syncedReports + syncedVisits + syncedInventoryRequests + syncedSensorReadings

    val failedTotal: Int
        get() = failedFarms + failedReports + failedVisits + failedInventoryRequests + failedSensorReadings

    val statusLabel: String
        get() = when {
            syncing -> "Retrying sync…"
            blockedByAccess -> "Blocked by officer access"
            waitingForConnectivity || !isOnline -> "Waiting for connectivity"
            failedTotal > 0 -> "Failed items need retry"
            pendingTotal > 0 -> "Pending upload"
            else -> "Up to date"
        }
}

data class ReferenceDataState(
    val catalogSearchQuery: String = "",
    val catalogFilter: String = "",
    val refreshing: Boolean = false,
    val refreshError: String? = null,
    val lastRefreshAt: Long? = null,
    val weatherRefreshing: Boolean = false,
    val weatherError: String? = null
)

class AgriScoutViewModel(
    private val authRepository: AuthRepository,
    private val farmRepository: FarmRepository,
    private val reportRepository: ReportRepository,
    private val farmVisitRepository: FarmVisitRepository,
    private val sensorRepository: SensorRepository,
    private val inventoryRepository: InventoryRepository,
    private val marketplaceRepository: MarketplaceRepository,
    private val detectionRepository: DetectionRepository,
    private val cropLifecycleEstimator: CropLifecycleEstimator,
    private val recommendationRepository: RecommendationRepository,
    private val catalogRepository: CatalogRepository,
    private val weatherRepository: WeatherRepository,
    private val notificationRepository: NotificationRepository,
    private val syncRepository: SyncRepository,
    private val locationService: LocationService,
    private val connectivityMonitor: NetworkConnectivityMonitor,
    private val appContext: Context
) : ViewModel() {

    private val _authForm = MutableStateFlow(AuthFormState())
    val authForm = _authForm.asStateFlow()

    private val _farmForm = MutableStateFlow(FarmFormState())
    val farmForm = _farmForm.asStateFlow()

    private val _reportForm = MutableStateFlow(ReportFormState())
    val reportForm = _reportForm.asStateFlow()

    private var unregisterConnectivity: (() -> Unit)? = null

    private val _farms = MutableStateFlow<List<FarmEntity>>(emptyList())
    val farms = _farms.asStateFlow()

    private val _reports = MutableStateFlow<List<FieldReportEntity>>(emptyList())
    val reports = _reports.asStateFlow()

    private val _inventoryRequests = MutableStateFlow<List<InventoryRequestEntity>>(emptyList())
    val inventoryRequests = _inventoryRequests.asStateFlow()

    private val _inventoryItems = MutableStateFlow<List<InventoryItemEntity>>(emptyList())
    val inventoryItems = _inventoryItems.asStateFlow()

    private val _supplierProducts = MutableStateFlow<List<SupplierProductEntity>>(emptyList())
    val supplierProducts = _supplierProducts.asStateFlow()

    private val _productRequests = MutableStateFlow<List<ProductRequestEntity>>(emptyList())
    val productRequests = _productRequests.asStateFlow()
    private val _officerHarvestListings =
        MutableStateFlow<List<com.example.agriscout.data.local.HarvestListingEntity>>(emptyList())
    val officerHarvestListings = _officerHarvestListings.asStateFlow()
    private val _officerHarvestRequests =
        MutableStateFlow<List<com.example.agriscout.data.local.HarvestRequestEntity>>(emptyList())
    val officerHarvestRequests = _officerHarvestRequests.asStateFlow()
    private val _marketplaceFollowUpRefreshing = MutableStateFlow(false)
    val marketplaceFollowUpRefreshing = _marketplaceFollowUpRefreshing.asStateFlow()
    private var harvestFollowUpJob: Job? = null

    private val _inventoryCatalog = MutableStateFlow(InventoryCatalogState())
    val inventoryCatalog = _inventoryCatalog.asStateFlow()

    private val _farmVisits = MutableStateFlow<List<FarmVisitEntity>>(emptyList())
    val farmVisits = _farmVisits.asStateFlow()

    private val _sensorDashboard = MutableStateFlow(SensorDashboardState())
    val sensorDashboard = _sensorDashboard.asStateFlow()

    private val _inventoryRequestForm = MutableStateFlow(InventoryRequestFormState())
    val inventoryRequestForm = _inventoryRequestForm.asStateFlow()

    private val _farmVisitForm = MutableStateFlow(FarmVisitFormState())
    val farmVisitForm = _farmVisitForm.asStateFlow()

    private val _recommendationState = MutableStateFlow(RecommendationState())
    val recommendationState = _recommendationState.asStateFlow()

    private val _catalog = MutableStateFlow<List<DiseaseCatalogEntity>>(emptyList())
    val catalog = _catalog.asStateFlow()

    private val _warnings = MutableStateFlow<List<WeatherWarningEntity>>(emptyList())
    val warnings = _warnings.asStateFlow()

    private val _weatherSnapshot = MutableStateFlow<WeatherSnapshotEntity?>(null)
    val weatherSnapshot = _weatherSnapshot.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _operations = MutableStateFlow(CrudOperationState())
    val operations = _operations.asStateFlow()

    private val _officerProfile = MutableStateFlow(OfficerProfileState())
    val officerProfile = _officerProfile.asStateFlow()

    private val _officerAccess = MutableStateFlow(OfficerAccessState())
    val officerAccess = _officerAccess.asStateFlow()

    private val _syncOverview = MutableStateFlow(SyncOverviewState())
    val syncOverview = _syncOverview.asStateFlow()

    private val _referenceData = MutableStateFlow(ReferenceDataState())
    val referenceData = _referenceData.asStateFlow()

    private var userDataJob: Job? = null
    private var catalogJob: Job? = null
    private var warningJob: Job? = null
    private var weatherJob: Job? = null
    private var sensorJob: Job? = null
    private var recommendationJob: Job? = null

    val dashboard: StateFlow<DashboardState> = combine(_farms, _reports, _inventoryRequests) { farms, reports, requests ->
        // Derive overview numbers from the same live lists used by the screens.
        // This keeps the dashboard current after login, inserts, deletes, and edits.
        DashboardState(
            farmCount = farms.size,
            reportCount = reports.size,
            pendingFarmCount = farms.count { it.syncStatus == SyncStatus.PENDING },
            pendingReportCount = reports.count { it.syncStatus == SyncStatus.PENDING },
            failedFarmCount = farms.count { it.syncStatus == SyncStatus.FAILED },
            failedReportCount = reports.count { it.syncStatus == SyncStatus.FAILED },
            pendingInventoryCount = requests.count { it.status.equals("Pending", ignoreCase = true) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    init {
        _syncOverview.value = _syncOverview.value.copy(isOnline = connectivityMonitor.isOnline())
        unregisterConnectivity = connectivityMonitor.registerConnectivityListener { online ->
            val wasOffline = !_syncOverview.value.isOnline
            _syncOverview.value = _syncOverview.value.copy(
                isOnline = online,
                waitingForConnectivity = if (online) false else _syncOverview.value.waitingForConnectivity || _syncOverview.value.pendingTotal > 0 || _syncOverview.value.failedTotal > 0
            )
            if (online && wasOffline) {
                scheduleSync(force = true)
            }
        }
        schedulePeriodicSync()
        refreshOfficerProfile()
        observeCurrentUserData()
        observeReferenceData()
        refreshSyncOverview()
        refreshOfficerAccess()
    }

    override fun onCleared() {
        unregisterConnectivity?.invoke()
        unregisterConnectivity = null
        super.onCleared()
    }

    fun updateAuth(email: String = _authForm.value.email, password: String = _authForm.value.password) {
        _authForm.value = _authForm.value.copy(email = email, password = password, message = null)
    }

    fun login(onSuccess: () -> Unit) = authenticate(onSuccess) {
        authRepository.login(_authForm.value.email.trim(), _authForm.value.password)
    }

    fun register(onSuccess: () -> Unit) = authenticate(onSuccess) {
        authRepository.register(_authForm.value.email.trim(), _authForm.value.password)
    }

    private fun authenticate(onSuccess: () -> Unit, block: suspend () -> Unit) {
        val validationMessage = validateAuthForm(_authForm.value)
        if (validationMessage != null) {
            _authForm.value = _authForm.value.copy(message = validationMessage)
            return
        }
        viewModelScope.launch {
            _authForm.value = _authForm.value.copy(loading = true, message = null)
            runCatching { block() }
                .onSuccess {
                    claimLegacyLocalDataForCurrentUser()
                    observeCurrentUserData()
                    val access = refreshOfficerAccessInternal()
                    if (access.canSync) {
                        runCatching { syncRepository.syncPendingData() }
                            .onSuccess {
                                _message.value = it.message
                                if (it.failures > 0) {
                                    scheduleSync()
                                }
                            }
                            .onFailure {
                                _message.value = it.localizedMessage ?: "Signed in. Cloud sync will retry when network is available."
                                scheduleSync()
                            }
                    } else {
                        _message.value = access.message.ifBlank {
                            "Signed in. Administrator approval is required before cloud sync."
                        }
                    }
                    _authForm.value = AuthFormState()
                    onSuccess()
                }
                .onFailure {
                    _authForm.value = _authForm.value.copy(
                        message = it.localizedMessage ?: "Unable to sign in. Check your connection and try again.",
                        loading = false
                    )
                }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        authRepository.logout()
        _officerAccess.value = OfficerAccessState()
        observeCurrentUserData()
        onLoggedOut()
    }

    fun refreshOfficerAccess(onComplete: ((OfficerAccessState) -> Unit)? = null) {
        viewModelScope.launch {
            val access = refreshOfficerAccessInternal()
            onComplete?.invoke(access)
        }
    }

    fun editFarm(farm: FarmEntity?) {
        _farmForm.value = farm?.let {
            FarmFormState(
                id = it.id,
                farmName = it.farmName,
                farmerName = it.farmerName,
                cropType = it.cropType,
                locationText = it.locationText,
                landSize = it.landSize,
                notes = it.notes,
                plantingDateText = formatInputDate(it.plantingDate),
                photoLocalUri = it.photoLocalUri,
                remotePhotoUrl = it.remotePhotoUrl,
                latitude = it.latitude,
                longitude = it.longitude,
                gpsAccuracyMeters = it.gpsAccuracyMeters,
                gpsCapturedAt = it.gpsCapturedAt,
                gpsSource = it.gpsSource,
                assignedDeviceId = it.assignedDeviceId.orEmpty(),
                assignedCameraDeviceId = it.assignedCameraDeviceId.orEmpty()
            )
        } ?: FarmFormState()
    }

    fun updateFarmForm(transform: FarmFormState.() -> FarmFormState) {
        _farmForm.value = _farmForm.value.transform().copy(message = null)
    }

    fun saveFarm(onSaved: () -> Unit) {
        val form = _farmForm.value
        val validationMessage = validateFarmForm(form)
        if (validationMessage != null) {
            _farmForm.value = form.copy(message = validationMessage)
            return
        }
        val existing = _farms.value.firstOrNull { it.id == form.id }
        val plantingDate = parseInputDate(form.plantingDateText)
        viewModelScope.launch {
            _farmForm.value = form.copy(saving = true, message = null)
            runCatching {
                val userId = authRepository.requireCurrentUserId()
                farmRepository.saveFarm(
                    existing = existing,
                    userId = userId,
                    farmName = form.farmName,
                    farmerName = form.farmerName,
                    cropType = form.cropType,
                    locationText = form.locationText,
                    landSize = form.landSize,
                    notes = form.notes,
                    latitude = form.latitude,
                    longitude = form.longitude,
                    gpsAccuracyMeters = form.gpsAccuracyMeters,
                    gpsCapturedAt = form.gpsCapturedAt,
                    gpsSource = form.gpsSource,
                    plantingDate = plantingDate,
                    assignedDeviceId = form.assignedDeviceId.trim().ifBlank { null },
                    assignedCameraDeviceId = form.assignedCameraDeviceId.trim().ifBlank { null },
                    photoLocalUri = form.photoLocalUri,
                    remotePhotoUrl = form.remotePhotoUrl
                )
                scheduleSync()
                refreshSyncOverview()
            }
                .onSuccess {
                    _farmForm.value = FarmFormState()
                    onSaved()
                }
                .onFailure {
                    _farmForm.value = _farmForm.value.copy(
                        saving = false,
                        message = it.localizedMessage ?: "Unable to save farm. Please try again."
                    )
                }
        }
    }

    fun captureFarmLocation() = viewModelScope.launch {
        _farmForm.value = _farmForm.value.copy(locationLoading = true, message = "Getting farm GPS pin...")
        runCatching { locationService.getCurrentLocation() }
            .onSuccess { location ->
                _farmForm.value = if (location == null) {
                    _farmForm.value.copy(
                        locationLoading = false,
                        message = "Unable to get farm location. Enable GPS/location services and try again."
                    )
                } else {
                    _farmForm.value.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        gpsAccuracyMeters = location.accuracyMeters?.toDouble(),
                        gpsCapturedAt = location.capturedAt,
                        gpsSource = location.source,
                        locationLoading = false,
                        message = locationCaptureMessage("Farm GPS pin", location.accuracyMeters, location.source)
                    )
                }
            }
            .onFailure {
                _farmForm.value = _farmForm.value.copy(
                    locationLoading = false,
                    message = it.localizedMessage ?: "Farm location failed. Enable GPS/location services and try again."
                )
            }
    }

    fun updateInventoryRequestForm(transform: InventoryRequestFormState.() -> InventoryRequestFormState) {
        val previous = _inventoryRequestForm.value
        val next = previous.transform().copy(message = null)
        _inventoryRequestForm.value = if (next.itemType != previous.itemType) {
            next.copy(inventoryItemId = null)
        } else {
            next
        }
    }

    fun submitInventoryRequest() {
        val form = _inventoryRequestForm.value
        val validationMessage = validateInventoryRequestForm(form)
        if (validationMessage != null) {
            _inventoryRequestForm.value = form.copy(message = validationMessage)
            return
        }
        viewModelScope.launch {
            _inventoryRequestForm.value = form.copy(submitting = true, message = null)
            runCatching {
                inventoryRepository.submitRequest(
                    userId = authRepository.requireCurrentUserId(),
                    farmId = form.farmId,
                    itemType = form.itemType,
                    quantity = form.quantity,
                    reason = form.reason,
                    inventoryItemId = form.inventoryItemId
                )
            }.onSuccess {
                _inventoryRequestForm.value = InventoryRequestFormState(farmId = _farms.value.firstOrNull()?.id.orEmpty())
                _message.value = "Inventory request saved locally with pending status."
                scheduleSync()
            }.onFailure {
                _inventoryRequestForm.value = _inventoryRequestForm.value.copy(
                    submitting = false,
                    message = it.localizedMessage ?: "Unable to save inventory request."
                )
            }
        }
    }

    fun stockPreview(itemType: String, inventoryItemId: String? = null) =
        inventoryRepository.stockFor(itemType, inventoryItemId, _inventoryItems.value)

    fun refreshInventoryCatalog() = viewModelScope.launch {
        if (_inventoryCatalog.value.refreshing) return@launch
        _inventoryCatalog.value = _inventoryCatalog.value.copy(refreshing = true, refreshError = null)
        val inventoryResult = runCatching { inventoryRepository.refreshItemsFromRemote() }
        // Marketplace catalog is optional on this screen; never block warehouse stock refresh.
        runCatching { marketplaceRepository.refreshSupplierProductsFromRemote() }
        inventoryResult
            .onSuccess {
                _inventoryCatalog.value = InventoryCatalogState(
                    refreshing = false,
                    lastRefreshedAt = System.currentTimeMillis()
                )
            }
            .onFailure {
                val message = it.localizedMessage ?: "Unable to refresh inventory. Cached stock remains available offline."
                _inventoryCatalog.value = _inventoryCatalog.value.copy(
                    refreshing = false,
                    refreshError = message
                )
            }
    }

    fun requestSupplierProduct(
        farmId: String?,
        recommendation: Recommendation,
        product: SupplierProductEntity,
        quantity: String = "1"
    ) = viewModelScope.launch {
        val userId = authRepository.currentUserId ?: return@launch
        runCatching {
            marketplaceRepository.createProductRequest(
                userId = userId,
                farmId = farmId,
                recommendation = recommendation,
                product = product,
                quantity = quantity
            )
        }.onSuccess {
            _message.value = "Supplier product request created for ${product.name}."
            scheduleSync()
        }.onFailure {
            _message.value = it.localizedMessage ?: "Unable to create supplier product request."
        }
    }

    fun publishHarvestListing(
        farmId: String,
        recommendation: Recommendation
    ) = viewModelScope.launch {
        val userId = authRepository.currentUserId ?: return@launch
        val farm = _farms.value.firstOrNull { it.id == farmId }
        if (farm == null) {
            _message.value = "Farm not found for harvest listing."
            return@launch
        }
        runCatching {
            marketplaceRepository.publishHarvestListing(
                officerUid = userId,
                farm = farm,
                recommendation = recommendation
            )
        }.onSuccess { listing ->
            _message.value =
                "Harvest listing queued for ${listing.cropType} at ${listing.farmName}. " +
                    "Awaiting admin verification before buyers can see it."
            scheduleSync()
        }.onFailure {
            _message.value = it.localizedMessage ?: "Unable to publish harvest listing."
        }
    }

    fun cancelSupplierProductRequest(requestId: String) = viewModelScope.launch {
        runCatching {
            marketplaceRepository.cancelProductRequest(requestId)
        }.onSuccess {
            _message.value = "Supplier request cancelled. It will sync when online."
            scheduleSync()
        }.onFailure {
            _message.value = it.localizedMessage ?: "Unable to cancel supplier request."
        }
    }

    fun respondToHarvestRequest(
        requestId: String,
        status: String,
        officerNote: String? = null
    ) = viewModelScope.launch {
        runCatching {
            marketplaceRepository.respondToHarvestRequest(
                requestId = requestId,
                status = status,
                officerNote = officerNote
            )
        }.onSuccess {
            _message.value = when (status.lowercase()) {
                "accepted" -> "Buyer interest accepted. Syncing response…"
                "rejected" -> "Buyer interest declined. Syncing response…"
                else -> "Harvest request marked under review."
            }
            scheduleSync()
        }.onFailure {
            _message.value = it.localizedMessage ?: "Unable to update harvest request."
        }
    }

    fun refreshMarketplaceFollowUps() = viewModelScope.launch {
        val userId = authRepository.currentUserId ?: return@launch
        _marketplaceFollowUpRefreshing.value = true
        runCatching {
            marketplaceRepository.refreshSupplierProductsFromRemote()
            marketplaceRepository.refreshHarvestMarketplaceFromRemote(userId)
            if (_officerAccess.value.canSync && connectivityMonitor.isOnline()) {
                syncRepository.syncPendingData()
            }
        }.onSuccess {
            _marketplaceFollowUpRefreshing.value = false
        }.onFailure {
            _marketplaceFollowUpRefreshing.value = false
            _message.value = it.localizedMessage ?: "Unable to refresh marketplace follow-ups."
        }
    }

    fun updateFarmVisitForm(transform: FarmVisitFormState.() -> FarmVisitFormState) {
        _farmVisitForm.value = _farmVisitForm.value.transform().copy(message = null)
    }

    fun prepareFarmVisit(farmId: String) {
        _farmVisitForm.value = FarmVisitFormState(farmId = farmId)
    }

    fun submitFarmVisit() {
        val form = _farmVisitForm.value
        val validationMessage = validateFarmVisitForm(form)
        if (validationMessage != null) {
            _farmVisitForm.value = form.copy(message = validationMessage)
            return
        }
        viewModelScope.launch {
            _farmVisitForm.value = form.copy(saving = true, message = null)
            runCatching {
                farmVisitRepository.saveVisit(
                    userId = authRepository.requireCurrentUserId(),
                    farmId = form.farmId,
                    cropCondition = form.cropCondition,
                    notes = form.notes,
                    cropConditionDetail = form.cropConditionDetail,
                    pestObservations = form.pestObservations,
                    growthStage = form.growthStage,
                    recommendedActions = form.recommendedActions,
                    followUpNotes = form.followUpNotes,
                    photoLocalUri = form.photoLocalUri,
                    latitude = form.latitude,
                    longitude = form.longitude,
                    gpsAccuracyMeters = form.gpsAccuracyMeters,
                    gpsCapturedAt = form.gpsCapturedAt,
                    gpsSource = form.gpsSource
                )
            }.onSuccess {
                _farmVisitForm.value = FarmVisitFormState(farmId = form.farmId)
                _message.value = if (_officerAccess.value.canSync) {
                    "Farm visit saved locally with pending sync."
                } else {
                    "Farm visit saved locally. Cloud sync waits until your officer account is active."
                }
                if (_officerAccess.value.canSync) {
                    scheduleSync()
                }
                refreshSyncOverview()
            }.onFailure {
                _farmVisitForm.value = _farmVisitForm.value.copy(
                    saving = false,
                    message = it.localizedMessage ?: "Unable to save farm visit."
                )
            }
        }
    }

    fun openSensorDashboard(farmId: String) {
        if (_sensorDashboard.value.selectedFarmId == farmId && sensorJob?.isActive == true) return
        sensorJob?.cancel()
        _sensorDashboard.value = SensorDashboardState(selectedFarmId = farmId, refreshing = true)
        val farm = _farms.value.firstOrNull { it.id == farmId }
        if (farm == null) {
            _sensorDashboard.value = SensorDashboardState(message = "Farm not found for sensor dashboard.")
            return
        }
        sensorJob = viewModelScope.launch {
            launch {
                sensorRepository.observeLatestForFarm(farmId).collect { reading ->
                    _sensorDashboard.value = _sensorDashboard.value.copy(
                        latestReading = reading,
                        refreshing = false,
                        isLiveData = sensorRepository.isLiveSource(reading),
                        isStale = sensorRepository.isReadingStale(reading),
                        dataSourceLabel = sensorSourceLabel(reading),
                        message = null
                    )
                    refreshRecommendations(farmId)
                }
            }
            launch {
                sensorRepository.observeRecentForFarm(farmId).collect { readings ->
                    _sensorDashboard.value = _sensorDashboard.value.copy(recentReadings = readings)
                }
            }
            // Pull cloud readings before simulation so live ESP32 data wins.
            if (_officerAccess.value.canSync && connectivityMonitor.isOnline()) {
                runCatching { syncRepository.syncPendingData() }
                    .onSuccess { applySyncResult(it) }
            } else {
                scheduleSync()
            }
            while (true) {
                if (_officerAccess.value.canSync && connectivityMonitor.isOnline()) {
                    runCatching { syncRepository.syncPendingData() }
                        .onSuccess { applySyncResult(it) }
                    runCatching { syncRepository.loadFarmIoTBundle(farmId) }
                        .onSuccess { bundle ->
                            val sensor = bundle.sensor?.takeIf {
                                farm.assignedDeviceId.isNullOrBlank() ||
                                    it.deviceId == farm.assignedDeviceId
                            } ?: bundle.sensor
                            val camera = bundle.camera?.takeIf {
                                farm.assignedCameraDeviceId.isNullOrBlank() ||
                                    it.deviceId == farm.assignedCameraDeviceId
                            } ?: bundle.camera
                            _sensorDashboard.value = _sensorDashboard.value.copy(
                                sensorModule = sensor,
                                cameraModule = camera,
                                captures = bundle.captures
                            )
                        }
                }
                val currentFarm = _farms.value.firstOrNull { it.id == farmId } ?: farm
                runCatching { sensorRepository.refreshReading(currentFarm) }
                    .onSuccess { result ->
                        _sensorDashboard.value = _sensorDashboard.value.copy(
                            refreshing = false,
                            latestReading = result.reading,
                            isLiveData = sensorRepository.isLiveSource(result.reading),
                            isStale = sensorRepository.isReadingStale(result.reading),
                            dataSourceLabel = sensorSourceLabel(result.reading),
                            message = null
                        )
                        if (result.wroteNew) {
                            refreshRecommendations(farmId)
                        }
                    }
                    .onFailure { error ->
                        _sensorDashboard.value = _sensorDashboard.value.copy(
                            refreshing = false,
                            message = error.localizedMessage ?: "Unable to refresh sensor data.",
                            isStale = sensorRepository.isReadingStale(_sensorDashboard.value.latestReading)
                        )
                    }
                delay(SENSOR_REFRESH_MS)
            }
        }
    }

    fun refreshSensorNow(farmId: String) = viewModelScope.launch {
        val farm = _farms.value.firstOrNull { it.id == farmId } ?: return@launch
        _sensorDashboard.value = _sensorDashboard.value.copy(refreshing = true, message = null)
        if (_officerAccess.value.canSync && connectivityMonitor.isOnline()) {
            runCatching { syncRepository.syncPendingData() }
                .onSuccess { applySyncResult(it) }
        }
        runCatching { sensorRepository.refreshReading(farm, forceSimulation = false) }
            .onSuccess { result ->
                _sensorDashboard.value = _sensorDashboard.value.copy(
                    refreshing = false,
                    latestReading = result.reading,
                    isLiveData = sensorRepository.isLiveSource(result.reading),
                    isStale = sensorRepository.isReadingStale(result.reading),
                    dataSourceLabel = sensorSourceLabel(result.reading),
                    message = null
                )
                if (result.wroteNew) {
                    refreshRecommendations(farmId)
                }
                scheduleSync()
            }
            .onFailure {
                _sensorDashboard.value = _sensorDashboard.value.copy(
                    refreshing = false,
                    message = it.localizedMessage ?: "Unable to refresh sensor data."
                )
            }
    }

    private fun sensorSourceLabel(reading: SensorReadingEntity?): String = when {
        reading == null -> "Waiting for data"
        sensorRepository.isLiveSource(reading) -> "Live device"
        else -> "Simulation"
    }

    fun openRecommendations(farmId: String) {
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch {
            val userId = authRepository.currentUserId
            runCatching {
                inventoryRepository.refreshItemsFromRemote()
                marketplaceRepository.refreshSupplierProductsFromRemote()
            }
            refreshRecommendations(farmId)
            launch {
                marketplaceRepository.observeHarvestListings(farmId).collect { listings ->
                    val current = _recommendationState.value
                    if (current.selectedFarmId == farmId) {
                        _recommendationState.value = current.copy(harvestListings = listings)
                    }
                }
            }
            launch {
                marketplaceRepository.observeHarvestRequests(farmId).collect { requests ->
                    val current = _recommendationState.value
                    if (current.selectedFarmId == farmId) {
                        _recommendationState.value = current.copy(harvestRequests = requests)
                    }
                }
            }
            if (userId != null) {
                runCatching {
                    marketplaceRepository.refreshHarvestMarketplaceFromRemote(userId)
                }
            }
            recommendationRepository.observeForFarm(farmId).collect { entities ->
                val farm = _farms.value.firstOrNull { it.id == farmId } ?: return@collect
                val recommendations = entities.map { it.toRecommendation() }
                _recommendationState.value = RecommendationState(
                    selectedFarmId = farmId,
                    lifecycleEstimate = cropLifecycleEstimator.estimate(farm.cropType, farm.plantingDate),
                    recommendations = recommendations,
                    supplierOffersByCategory = buildSupplierOffers(
                        recommendations = recommendations,
                        farmId = farmId,
                        products = _supplierProducts.value
                    ),
                    productRequests = _productRequests.value,
                    harvestListings = _recommendationState.value.harvestListings,
                    harvestRequests = _recommendationState.value.harvestRequests
                )
            }
        }
    }

    fun deleteFarm(farm: FarmEntity, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        if (_operations.value.deletingFarmId != null) return@launch
        _operations.value = _operations.value.copy(deletingFarmId = farm.id)
        runCatching { farmRepository.deleteFarm(farm) }
            .onSuccess { remoteDeleted ->
                _message.value = if (remoteDeleted) {
                    "Farm deleted locally and from cloud."
                } else {
                    "Farm deleted locally. Cloud delete could not complete."
                }
                onDeleted()
            }
            .onFailure { _message.value = it.localizedMessage ?: "Unable to delete farm. Please try again." }
        _operations.value = _operations.value.copy(deletingFarmId = null)
    }

    fun editReport(report: FieldReportEntity?, farmId: String? = null) {
        _reportForm.value = report?.let {
            ReportFormState(
                id = it.id,
                farmId = it.farmId,
                cropType = it.cropType,
                symptoms = it.symptoms,
                severity = it.severity,
                estimatedYield = it.estimatedYield,
                notes = it.notes,
                pestObservations = it.pestObservations,
                growthStage = it.growthStage,
                cropConditionDetail = it.cropConditionDetail,
                recommendedActions = it.recommendedActions,
                followUpNotes = it.followUpNotes,
                latitude = it.latitude,
                longitude = it.longitude,
                gpsAccuracyMeters = it.gpsAccuracyMeters,
                gpsCapturedAt = it.gpsCapturedAt,
                gpsSource = it.gpsSource,
                imageLocalUri = it.imageLocalUri,
                issueType = it.issueType,
                detectedIssue = it.detectedIssue,
                detectionConfidence = it.detectionConfidence,
                matchedRuleId = it.matchedRuleId,
                recommendation = it.recommendation,
                preventiveMeasures = it.preventiveMeasures,
                detectionExplanation = it.detectionExplanation,
                detectionSource = it.detectionSource,
                detectionUpdatedAt = it.detectionUpdatedAt
            )
        } ?: ReportFormState(farmId = farmId ?: _farms.value.firstOrNull()?.id.orEmpty())
    }

    fun updateReportForm(transform: ReportFormState.() -> ReportFormState) {
        val previousMessage = _reportForm.value.message
        val updated = _reportForm.value.transform()
        _reportForm.value = if (updated.message == previousMessage) {
            updated.copy(message = null)
        } else {
            updated
        }
    }

    fun saveReport(onSaved: () -> Unit) {
        val form = _reportForm.value
        val validationMessage = validateReportForm(form)
        if (validationMessage != null) {
            _reportForm.value = form.copy(message = validationMessage)
            return
        }
        val existing = _reports.value.firstOrNull { it.id == form.id }
        viewModelScope.launch {
            _reportForm.value = form.copy(saving = true, message = null)
            runCatching {
                val userId = authRepository.requireCurrentUserId()
                reportRepository.saveReport(
                    existing = existing,
                    userId = userId,
                    farmId = form.farmId,
                    cropType = form.cropType,
                    symptoms = form.symptoms,
                    severity = form.severity,
                    estimatedYield = form.estimatedYield,
                    notes = form.notes,
                    pestObservations = form.pestObservations,
                    growthStage = form.growthStage,
                    cropConditionDetail = form.cropConditionDetail,
                    recommendedActions = form.recommendedActions.ifBlank {
                        form.recommendation.orEmpty()
                    },
                    followUpNotes = form.followUpNotes,
                    latitude = form.latitude,
                    longitude = form.longitude,
                    gpsAccuracyMeters = form.gpsAccuracyMeters,
                    gpsCapturedAt = form.gpsCapturedAt,
                    gpsSource = form.gpsSource,
                    imageLocalUri = form.imageLocalUri,
                    detectionResult = form.toDetectionResult(),
                    preventiveMeasures = form.preventiveMeasures
                )
                scheduleSync()
                refreshSyncOverview()
            }
                .onSuccess {
                    _reportForm.value = ReportFormState(farmId = _farms.value.firstOrNull()?.id.orEmpty())
                    onSaved()
                }
                .onFailure {
                    _reportForm.value = _reportForm.value.copy(
                        saving = false,
                        message = it.localizedMessage ?: "Unable to save report. Please try again."
                    )
                }
        }
    }

    fun analyzeReportSymptoms() {
        val form = _reportForm.value
        val validationMessage = when {
            form.cropType.isBlank() -> "Crop type is required before analysis."
            form.symptoms.isBlank() -> "Symptoms are required before analysis."
            else -> null
        }
        if (validationMessage != null) {
            _reportForm.value = form.copy(message = validationMessage)
            return
        }
        viewModelScope.launch {
            _reportForm.value = form.copy(analyzing = true, message = null)
            runCatching {
                detectionRepository.analyze(
                    cropType = form.cropType,
                    symptoms = form.symptoms,
                    imageUri = form.imageLocalUri
                )
            }
                .onSuccess { result ->
                    _reportForm.value = _reportForm.value.copy(
                        issueType = result.issueType,
                        detectedIssue = result.issueName,
                        detectionConfidence = result.confidence,
                        matchedRuleId = result.matchedRuleId,
                        recommendation = result.recommendation,
                        preventiveMeasures = result.prevention,
                        detectionExplanation = result.explanation,
                        detectionSource = result.analysisSource,
                        recommendedActions = _reportForm.value.recommendedActions.ifBlank {
                            result.decisionSupportSummary()
                        },
                        detectionUpdatedAt = System.currentTimeMillis(),
                        analyzing = false,
                        message = if (result.issueType == IssueTypes.UNKNOWN) {
                            "No strong match found. Save for expert review."
                        } else {
                            "Analysis complete. Review the recommendation before saving."
                        }
                    )
                }
                .onFailure {
                    _reportForm.value = _reportForm.value.copy(
                        analyzing = false,
                        message = it.localizedMessage ?: "Analysis failed. Check symptoms and try again."
                    )
                }
        }
    }

    fun deleteReport(report: FieldReportEntity, onDeleted: () -> Unit = {}) = viewModelScope.launch {
        if (_operations.value.deletingReportId != null) return@launch
        _operations.value = _operations.value.copy(deletingReportId = report.id)
        runCatching { reportRepository.deleteReport(report) }
            .onSuccess { remoteDeleted ->
                _message.value = if (remoteDeleted) {
                    "Report deleted locally and from cloud."
                } else {
                    "Report deleted locally. Cloud delete could not complete."
                }
                onDeleted()
            }
            .onFailure { _message.value = it.localizedMessage ?: "Unable to delete report. Please try again." }
        _operations.value = _operations.value.copy(deletingReportId = null)
    }

    fun captureCurrentLocation() = viewModelScope.launch {
        _reportForm.value = _reportForm.value.copy(
            locationLoading = true,
            locationDenied = false,
            message = "Getting current location..."
        )
        runCatching { locationService.getCurrentLocation() }
            .onSuccess { location ->
                _reportForm.value = if (location == null) {
                    _reportForm.value.copy(
                        locationLoading = false,
                        message = "Unable to get location. Turn on GPS/location services and try again."
                    )
                } else {
                    _reportForm.value.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        gpsAccuracyMeters = location.accuracyMeters?.toDouble(),
                        gpsCapturedAt = location.capturedAt,
                        gpsSource = location.source,
                        locationLoading = false,
                        message = locationCaptureMessage("Location", location.accuracyMeters, location.source)
                    )
                }
            }
            .onFailure {
                _reportForm.value = _reportForm.value.copy(
                    locationLoading = false,
                    message = it.localizedMessage ?: "Location failed. Turn on GPS/location services and try again."
                )
            }
    }

    fun onLocationPermissionDenied(permanentlyDenied: Boolean) {
        _reportForm.value = _reportForm.value.copy(
            locationLoading = false,
            locationDenied = true,
            message = if (permanentlyDenied) {
                "Location permission is off. Enable it in app settings, then refresh location."
            } else {
                "Location permission denied. Allow it to auto-fill report coordinates."
            }
        )
    }

    fun captureFarmVisitLocation() = viewModelScope.launch {
        _farmVisitForm.value = _farmVisitForm.value.copy(
            locationLoading = true,
            message = "Getting visit GPS pin..."
        )
        runCatching { locationService.getCurrentLocation() }
            .onSuccess { location ->
                _farmVisitForm.value = if (location == null) {
                    _farmVisitForm.value.copy(
                        locationLoading = false,
                        message = "Unable to get visit location. Turn on GPS/location services and try again."
                    )
                } else {
                    _farmVisitForm.value.copy(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        gpsAccuracyMeters = location.accuracyMeters?.toDouble(),
                        gpsCapturedAt = location.capturedAt,
                        gpsSource = location.source,
                        locationLoading = false,
                        message = locationCaptureMessage("Visit GPS pin", location.accuracyMeters, location.source)
                    )
                }
            }
            .onFailure {
                _farmVisitForm.value = _farmVisitForm.value.copy(
                    locationLoading = false,
                    message = it.localizedMessage ?: "Visit location failed. Turn on GPS/location services and try again."
                )
            }
    }

    fun onFarmVisitLocationPermissionDenied(permanentlyDenied: Boolean) {
        _farmVisitForm.value = _farmVisitForm.value.copy(
            locationLoading = false,
            message = if (permanentlyDenied) {
                "Location permission is off. Enable it in app settings, then capture the visit location again."
            } else {
                "Location permission denied. Allow it to attach GPS coordinates to the visit photo."
            }
        )
    }

    fun refreshReferenceData() = viewModelScope.launch {
        if (_referenceData.value.refreshing) return@launch
        _referenceData.value = _referenceData.value.copy(refreshing = true, refreshError = null)
        runCatching { catalogRepository.refreshRemoteContent() }
            .onSuccess {
                _referenceData.value = _referenceData.value.copy(
                    refreshing = false,
                    refreshError = null,
                    lastRefreshAt = System.currentTimeMillis()
                )
                _message.value = "Catalog and warnings refreshed."
            }
            .onFailure {
                val errorMessage = it.localizedMessage ?: "Unable to refresh remote content. Cached entries are still available offline."
                _referenceData.value = _referenceData.value.copy(refreshing = false, refreshError = errorMessage)
                _message.value = errorMessage
            }
    }

    fun refreshWeather() = viewModelScope.launch {
        if (_referenceData.value.weatherRefreshing) return@launch
        val target = bestWeatherLocation()
        if (target == null) {
            _referenceData.value = _referenceData.value.copy(
                weatherError = "Capture a farm pin, report GPS location, or grant location permission before refreshing weather."
            )
            return@launch
        }
        _referenceData.value = _referenceData.value.copy(weatherRefreshing = true, weatherError = null)
        runCatching { weatherRepository.refresh(target.latitude, target.longitude, target.label) }
            .onSuccess {
                _referenceData.value = _referenceData.value.copy(
                    weatherRefreshing = false,
                    weatherError = null,
                    lastRefreshAt = System.currentTimeMillis()
                )
                _message.value = "Weather refreshed for ${it.locationLabel}."
            }
            .onFailure {
                val message = it.localizedMessage ?: "Unable to refresh weather. Cached weather remains available."
                _referenceData.value = _referenceData.value.copy(weatherRefreshing = false, weatherError = message)
                _message.value = message
            }
    }

    fun updateCatalogSearch(query: String) {
        _referenceData.value = _referenceData.value.copy(catalogSearchQuery = query)
    }

    fun updateCatalogFilter(filter: String) {
        _referenceData.value = _referenceData.value.copy(catalogFilter = filter)
    }

    fun syncNow() = viewModelScope.launch {
        if (_syncOverview.value.syncing) return@launch
        val access = if (!_officerAccess.value.loaded) {
            refreshOfficerAccessInternal()
        } else {
            _officerAccess.value
        }
        if (!access.canSync) {
            val blockedMessage = when (access.status) {
                OfficerAccessStatus.PENDING ->
                    "Cloud sync is unavailable while your officer account is awaiting administrator approval."
                OfficerAccessStatus.INACTIVE ->
                    "Cloud sync is blocked because this officer account is inactive."
                else ->
                    access.message.ifBlank { "Cloud sync is unavailable until officer access is verified as active." }
            }
            _syncOverview.value = _syncOverview.value.copy(
                syncing = false,
                blockedByAccess = true,
                waitingForConnectivity = false,
                lastResultSummary = blockedMessage,
                lastResultErrors = listOf(blockedMessage)
            )
            _message.value = blockedMessage
            return@launch
        }
        if (!connectivityMonitor.isOnline()) {
            val offlineMessage = "Waiting for connectivity. Pending data remains stored on this device."
            _syncOverview.value = _syncOverview.value.copy(
                syncing = false,
                blockedByAccess = false,
                isOnline = false,
                waitingForConnectivity = true,
                lastResultSummary = offlineMessage,
                lastResultErrors = listOf(offlineMessage)
            )
            _message.value = offlineMessage
            scheduleSync(force = false)
            return@launch
        }
        _syncOverview.value = _syncOverview.value.copy(
            syncing = true,
            blockedByAccess = false,
            waitingForConnectivity = false,
            isOnline = true,
            lastResultErrors = emptyList()
        )
        runCatching { syncRepository.syncPendingData() }
            .onSuccess {
                applySyncResult(it)
                refreshInventoryCatalog()
                _message.value = it.message
                if (it.waitingForConnectivity) {
                    scheduleSync(force = false)
                }
            }
            .onFailure {
                _syncOverview.value = _syncOverview.value.copy(
                    lastResultErrors = listOf(it.localizedMessage ?: "Sync failed. Check your connection and try again.")
                )
                _message.value = it.localizedMessage ?: "Sync failed. Check your connection and try again."
            }
        _syncOverview.value = _syncOverview.value.copy(syncing = false)
    }

    fun registerForPushAlerts() = viewModelScope.launch {
        runCatching { notificationRepository.registerCurrentDevice() }
            .onSuccess { _message.value = "Push alerts enabled for this officer." }
            .onFailure { _message.value = it.localizedMessage ?: "Unable to enable push alerts." }
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun observeCurrentUserData() {
        refreshOfficerProfile()
        val userId = authRepository.currentUserId
        userDataJob?.cancel()
        if (userId == null) {
            _farms.value = emptyList()
            _reports.value = emptyList()
            _farmVisits.value = emptyList()
            _inventoryRequests.value = emptyList()
            _inventoryItems.value = emptyList()
            _supplierProducts.value = emptyList()
            _productRequests.value = emptyList()
            _officerHarvestListings.value = emptyList()
            _officerHarvestRequests.value = emptyList()
            _inventoryCatalog.value = InventoryCatalogState()
            _farmVisitForm.value = FarmVisitFormState()
            _sensorDashboard.value = SensorDashboardState()
            _recommendationState.value = RecommendationState()
            _officerAccess.value = OfficerAccessState()
            sensorJob?.cancel()
            harvestFollowUpJob?.cancel()
            _syncOverview.value = SyncOverviewState()
            return
        }
        refreshOfficerAccess()
        userDataJob = viewModelScope.launch {
            claimLegacyLocalDataForCurrentUser()
            refreshInventoryCatalog()
            launch {
                farmRepository.observeFarms(userId).collect { farms ->
                    _farms.value = farms
                    refreshSyncOverviewFromLists()
                }
            }
            launch {
                reportRepository.observeReports(userId).collect { reports ->
                    _reports.value = reports
                    refreshSyncOverviewFromLists()
                }
            }
            launch {
                inventoryRepository.observeRequests(userId).collect { requests ->
                    _inventoryRequests.value = requests
                    refreshSyncOverviewFromLists()
                }
            }
            launch {
                marketplaceRepository.observeProductRequests(userId).collect { requests ->
                    _productRequests.value = requests
                    val current = _recommendationState.value
                    if (current.selectedFarmId != null) {
                        _recommendationState.value = current.copy(productRequests = requests)
                    }
                }
            }
            harvestFollowUpJob?.cancel()
            harvestFollowUpJob = launch {
                launch {
                    marketplaceRepository.observeHarvestListingsForOfficer(userId).collect { listings ->
                        _officerHarvestListings.value = listings
                    }
                }
                launch {
                    marketplaceRepository.observeHarvestRequestsForOfficer(userId).collect { requests ->
                        _officerHarvestRequests.value = requests
                    }
                }
            }
            launch {
                farmVisitRepository.observeVisits(userId).collect { visits ->
                    _farmVisits.value = visits
                    refreshSyncOverviewFromLists()
                }
            }
        }
    }

    private fun observeReferenceData() {
        catalogJob = viewModelScope.launch { catalogRepository.observeCatalog().collect { _catalog.value = it } }
        warningJob = viewModelScope.launch { catalogRepository.observeWarnings().collect { _warnings.value = it } }
        weatherJob = viewModelScope.launch { weatherRepository.observeLatestSnapshot().collect { _weatherSnapshot.value = it } }
        viewModelScope.launch { inventoryRepository.observeItems().collect { _inventoryItems.value = it } }
        viewModelScope.launch {
            marketplaceRepository.observeSupplierProducts().collect { products ->
                _supplierProducts.value = products
                val current = _recommendationState.value
                if (current.recommendations.isNotEmpty()) {
                    _recommendationState.value = current.copy(
                        supplierOffersByCategory = buildSupplierOffers(
                            recommendations = current.recommendations,
                            farmId = current.selectedFarmId,
                            products = products
                        )
                    )
                }
            }
        }
    }

    private suspend fun bestWeatherLocation(): WeatherLocationTarget? {
        _farms.value.firstOrNull { it.latitude != null && it.longitude != null }?.let {
            return WeatherLocationTarget(it.latitude!!, it.longitude!!, it.farmName)
        }
        _reports.value.firstOrNull { it.latitude != null && it.longitude != null }?.let {
            return WeatherLocationTarget(it.latitude!!, it.longitude!!, it.cropType.ifBlank { "Current report area" })
        }
        return locationService.getCurrentLocation()?.let {
            WeatherLocationTarget(it.latitude, it.longitude, "Current location")
        }
    }

    private fun refreshOfficerProfile() {
        val session = authRepository.currentSession
        _officerProfile.value = OfficerProfileState(
            isFirebaseConfigured = authRepository.isConfigured,
            isLoggedIn = session != null,
            email = session?.email,
            userId = session?.userId
        )
    }

    private suspend fun refreshOfficerAccessInternal(): OfficerAccessState {
        if (!authRepository.isLoggedIn()) {
            val cleared = OfficerAccessState()
            _officerAccess.value = cleared
            return cleared
        }
        _officerAccess.value = _officerAccess.value.copy(loading = true, error = null)
        val resolved = runCatching { authRepository.fetchOfficerAccess() }
            .fold(
                onSuccess = { record ->
                    if (record == null) {
                        officerAccessFromStatus(
                            role = OfficerRoles.FIELD_OFFICER,
                            status = null,
                            error = "Unable to verify officer access because Firebase is not fully configured."
                        )
                    } else {
                        officerAccessFromStatus(role = record.role, status = record.status)
                    }
                },
                onFailure = { error ->
                    officerAccessFromStatus(
                        role = _officerAccess.value.role,
                        status = _officerAccess.value.status,
                        error = error.localizedMessage
                            ?: "Unable to verify officer access. Check your connection and try again."
                    ).copy(loaded = false)
                }
            )
        _officerAccess.value = resolved.copy(loading = false)
        _syncOverview.value = _syncOverview.value.copy(blockedByAccess = !_officerAccess.value.canSync)
        return _officerAccess.value
    }

    fun scheduleSync(force: Boolean = false) {
        if (!_officerAccess.value.canSync) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "agri_scout_sync",
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun claimLegacyLocalDataForCurrentUser() {
        val userId = authRepository.currentUserId ?: return
        farmRepository.claimLegacyLocalData(userId)
        reportRepository.claimLegacyLocalData(userId)
        farmVisitRepository.claimLegacyLocalData(userId)
        inventoryRepository.claimLegacyLocalData(userId)
    }

    private suspend fun refreshRecommendations(farmId: String) {
        val userId = authRepository.currentUserId ?: return
        val farm = _farms.value.firstOrNull { it.id == farmId } ?: return
        val latestObservation = _reports.value
            .filter { it.farmId == farmId }
            .maxByOrNull { it.updatedAt }
        val sensorReading = _sensorDashboard.value.latestReading?.takeIf { it.farmId == farmId }
            ?: sensorRepository.observeLatestForFarm(farmId).firstOrNull()
        val entities = recommendationRepository.refreshForFarm(
            userId = userId,
            farm = farm,
            sensorReading = sensorReading,
            weatherSnapshot = _weatherSnapshot.value,
            latestObservationSeverity = latestObservation?.severity,
            detectedIssueId = latestObservation?.matchedRuleId
        )
        val recommendations = entities.map { it.toRecommendation() }
        _recommendationState.value = RecommendationState(
            selectedFarmId = farmId,
            lifecycleEstimate = cropLifecycleEstimator.estimate(farm.cropType, farm.plantingDate),
            recommendations = recommendations,
            supplierOffersByCategory = buildSupplierOffers(
                recommendations = recommendations,
                farmId = farmId,
                products = _supplierProducts.value
            ),
            productRequests = _productRequests.value,
            harvestListings = _recommendationState.value.harvestListings,
            harvestRequests = _recommendationState.value.harvestRequests
        )
        scheduleSync()
    }

    private fun buildSupplierOffers(
        recommendations: List<Recommendation>,
        farmId: String?,
        products: List<SupplierProductEntity>
    ): Map<String, List<SupplierProductOffer>> {
        val cropType = farmId?.let { id -> _farms.value.firstOrNull { it.id == id }?.cropType }
        return recommendations
            .mapNotNull { recommendation ->
                val category = recommendation.productCategory?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val offers = SupplierProductMatcher.match(
                    productCategory = category,
                    cropType = cropType,
                    preferredNames = listOfNotNull(
                        recommendation.suggestedItemName,
                        recommendation.alternativeItemName
                    ),
                    products = products
                )
                category to offers
            }
            .toMap()
    }

    private fun refreshSyncOverviewFromLists() {
        val farms = _farms.value
        val reports = _reports.value
        val visits = _farmVisits.value
        val requests = _inventoryRequests.value
        _syncOverview.value = _syncOverview.value.copy(
            pendingFarms = farms.count { it.syncStatus == SyncStatus.PENDING },
            pendingReports = reports.count { it.syncStatus == SyncStatus.PENDING },
            pendingVisits = visits.count { it.syncStatus == SyncStatus.PENDING },
            pendingInventoryRequests = requests.count { it.syncStatus == SyncStatus.PENDING },
            syncedFarms = farms.count { it.syncStatus == SyncStatus.SYNCED },
            syncedReports = reports.count { it.syncStatus == SyncStatus.SYNCED },
            syncedVisits = visits.count { it.syncStatus == SyncStatus.SYNCED },
            syncedInventoryRequests = requests.count { it.syncStatus == SyncStatus.SYNCED },
            failedFarms = farms.count { it.syncStatus == SyncStatus.FAILED },
            failedReports = reports.count { it.syncStatus == SyncStatus.FAILED },
            failedVisits = visits.count { it.syncStatus == SyncStatus.FAILED },
            failedInventoryRequests = requests.count { it.syncStatus == SyncStatus.FAILED }
        )
    }

    private fun refreshSyncOverview() = viewModelScope.launch {
        val counts = syncRepository.getSyncStatusCounts()
        applySyncCounts(counts)
    }

    private fun applySyncResult(result: SyncResult) {
        applySyncCounts(result.statusCounts)
        _syncOverview.value = _syncOverview.value.copy(
            lastResultSummary = result.message,
            lastResultErrors = result.errors,
            waitingForConnectivity = result.waitingForConnectivity,
            blockedByAccess = false,
            isOnline = connectivityMonitor.isOnline()
        )
    }

    private fun applySyncCounts(counts: SyncStatusCounts) {
        _syncOverview.value = _syncOverview.value.copy(
            pendingFarms = counts.pendingFarms,
            pendingReports = counts.pendingReports,
            pendingVisits = counts.pendingVisits,
            pendingInventoryRequests = counts.pendingInventoryRequests,
            pendingSensorReadings = counts.pendingSensorReadings,
            syncedFarms = counts.syncedFarms,
            syncedReports = counts.syncedReports,
            syncedVisits = counts.syncedVisits,
            syncedInventoryRequests = counts.syncedInventoryRequests,
            syncedSensorReadings = counts.syncedSensorReadings,
            failedFarms = counts.failedFarms,
            failedReports = counts.failedReports,
            failedVisits = counts.failedVisits,
            failedInventoryRequests = counts.failedInventoryRequests,
            failedSensorReadings = counts.failedSensorReadings
        )
    }

    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "agri_scout_periodic_sync"
        private const val SENSOR_REFRESH_MS = 20_000L
    }
}

internal fun validateAuthForm(form: AuthFormState): String? = when {
    form.email.isBlank() -> "Enter your officer email address."
    "@" !in form.email || "." !in form.email.substringAfter("@") -> "Enter a valid officer email address."
    form.password.length < 6 -> "Enter a password with at least 6 characters."
    else -> null
}

internal fun validateFarmForm(form: FarmFormState): String? = when {
    form.farmName.isBlank() -> "Farm name is required."
    form.farmerName.isBlank() -> "Farmer/owner name is required."
    form.cropType.isBlank() -> "Crop type is required."
    form.locationText.isBlank() -> "District/location is required."
    form.plantingDateText.isNotBlank() && parseInputDate(form.plantingDateText) == null -> "Planting date must use yyyy-MM-dd format."
    else -> null
}

internal fun validateReportForm(form: ReportFormState): String? = when {
    form.farmId.isBlank() -> "Select a farm before saving this report."
    form.cropType.isBlank() -> "Crop type is required for this report."
    form.symptoms.isBlank() -> "Disease/pest symptoms are required."
    form.severity.isBlank() -> "Severity is required."
    else -> null
}

internal fun validateInventoryRequestForm(form: InventoryRequestFormState): String? = when {
    form.itemType.isBlank() -> "Select the requested item type."
    form.quantity.isBlank() -> "Quantity is required."
    form.reason.isBlank() -> "Reason is required."
    else -> null
}

internal fun validateFarmVisitForm(form: FarmVisitFormState): String? = when {
    form.farmId.isBlank() -> "Select a farm before saving this visit."
    form.cropCondition.isBlank() -> "Crop condition is required."
    form.notes.isBlank() -> "Visit notes are required."
    else -> null
}

internal fun officerAccessFromStatus(
    role: String?,
    status: String?,
    error: String? = null
): OfficerAccessState {
    val normalizedStatus = status?.lowercase(Locale.getDefault())
    val canOperate = normalizedStatus == OfficerAccessStatus.ACTIVE
    val title = when (normalizedStatus) {
        OfficerAccessStatus.PENDING -> "Awaiting administrator approval"
        OfficerAccessStatus.INACTIVE -> "Officer account inactive"
        OfficerAccessStatus.ACTIVE -> "Officer access active"
        else -> "Officer access required"
    }
    val message = when {
        !error.isNullOrBlank() && normalizedStatus == null -> error
        normalizedStatus == OfficerAccessStatus.PENDING ->
            "Your field officer account is pending approval. You can stay signed in, but farm operations and cloud sync unlock after an administrator activates your account."
        normalizedStatus == OfficerAccessStatus.INACTIVE ->
            "This officer account is inactive. Contact an administrator to restore access before continuing field operations."
        normalizedStatus == OfficerAccessStatus.ACTIVE ->
            "Your officer account is active. Farm monitoring and cloud sync are available."
        else ->
            error ?: "Sign in succeeded, but officer access could not be confirmed yet. Refresh status or contact an administrator."
    }
    return OfficerAccessState(
        role = role ?: OfficerRoles.FIELD_OFFICER,
        status = normalizedStatus,
        loading = false,
        loaded = error.isNullOrBlank() || normalizedStatus != null,
        error = error,
        canOperate = canOperate,
        canSync = canOperate,
        title = title,
        message = message
    )
}

internal fun locationCaptureMessage(label: String, accuracyMeters: Float?, source: String): String {
    val accuracyText = accuracyMeters?.let { " (±${it.toInt()} m)" }.orEmpty()
    val sourceText = when (source) {
        "last_known" -> " from last known location"
        else -> " from device GPS"
    }
    return "$label updated$accuracyText$sourceText."
}

internal fun parseInputDate(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching {
        inputDateFormatter().parse(value.trim())?.time
    }.getOrNull()
}

internal fun formatInputDate(value: Long?): String {
    return value?.let { inputDateFormatter().format(it) }.orEmpty()
}

private fun inputDateFormatter(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
    isLenient = false
}

private fun ReportFormState.toDetectionResult(): DetectionResult? {
    val issue = detectedIssue?.takeIf { it.isNotBlank() } ?: return null
    return DetectionResult(
        issueType = issueType ?: IssueTypes.UNKNOWN,
        issueName = issue,
        confidence = detectionConfidence ?: 0,
        matchedRuleId = matchedRuleId,
        matchedKeywords = emptyList(),
        treatment = recommendation.orEmpty(),
        prevention = preventiveMeasures.orEmpty(),
        explanation = detectionExplanation.orEmpty(),
        analysisSource = detectionSource ?: "rules"
    )
}

class AgriScoutViewModelFactory(
    private val appContainer: AppContainer,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AgriScoutViewModel::class.java)) {
            return AgriScoutViewModel(
                authRepository = appContainer.authRepository,
                farmRepository = appContainer.farmRepository,
                reportRepository = appContainer.reportRepository,
                farmVisitRepository = appContainer.farmVisitRepository,
                sensorRepository = appContainer.sensorRepository,
                inventoryRepository = appContainer.inventoryRepository,
                marketplaceRepository = appContainer.marketplaceRepository,
                detectionRepository = appContainer.detectionRepository,
                cropLifecycleEstimator = appContainer.cropLifecycleEstimator,
                recommendationRepository = appContainer.recommendationRepository,
                catalogRepository = appContainer.catalogRepository,
                weatherRepository = appContainer.weatherRepository,
                notificationRepository = appContainer.notificationRepository,
                syncRepository = appContainer.syncRepository,
                locationService = appContainer.locationService,
                connectivityMonitor = appContainer.connectivityMonitor,
                appContext = appContext
            ) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}

private data class WeatherLocationTarget(
    val latitude: Double,
    val longitude: Double,
    val label: String
)
