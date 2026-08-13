package com.example.agriscout.di

import android.content.Context
import com.example.agriscout.BuildConfig
import com.example.agriscout.auth.FirebaseAuthService
import com.example.agriscout.data.local.AgriScoutDatabase
import com.example.agriscout.data.remote.CatalogRemoteService
import com.example.agriscout.data.remote.FcmRemoteService
import com.example.agriscout.data.remote.SyncRemoteService
import com.example.agriscout.data.remote.WeatherRemoteService
import com.example.agriscout.data.repository.AuthRepository
import com.example.agriscout.data.repository.CatalogRepository
import com.example.agriscout.data.repository.DetectionRepository
import com.example.agriscout.data.repository.FarmVisitRepository
import com.example.agriscout.data.repository.FarmRepository
import com.example.agriscout.data.repository.InventoryRepository
import com.example.agriscout.data.repository.MarketplaceRepository
import com.example.agriscout.data.repository.NotificationRepository
import com.example.agriscout.data.repository.RecommendationRepository
import com.example.agriscout.data.repository.ReportRepository
import com.example.agriscout.data.repository.SensorRepository
import com.example.agriscout.data.repository.SyncRepository
import com.example.agriscout.data.repository.WeatherRepository
import com.example.agriscout.data.simulation.SimulatedIoTDataSource
import com.example.agriscout.crop.CropLifecycleEstimator
import com.example.agriscout.detection.RuleBasedDetectionEngine
import com.example.agriscout.detection.DetectionFusionEngine
import com.example.agriscout.detection.ImageDiseaseClassifier
import com.example.agriscout.location.LocationService
import com.example.agriscout.calendar.AgronomicOrchestrator
import com.example.agriscout.calendar.CropCalendarEngine
import com.example.agriscout.calendar.CropCalendarLoader
import com.example.agriscout.recommendation.RuleBasedRecommendationEngine
import com.example.agriscout.sync.NetworkConnectivityMonitor
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage

interface AppContainer {
    val authRepository: AuthRepository
    val farmRepository: FarmRepository
    val reportRepository: ReportRepository
    val farmVisitRepository: FarmVisitRepository
    val sensorRepository: SensorRepository
    val inventoryRepository: InventoryRepository
    val marketplaceRepository: MarketplaceRepository
    val detectionRepository: DetectionRepository
    val cropLifecycleEstimator: CropLifecycleEstimator
    val agronomicOrchestrator: AgronomicOrchestrator
    val recommendationRepository: RecommendationRepository
    val catalogRepository: CatalogRepository
    val weatherRepository: WeatherRepository
    val notificationRepository: NotificationRepository
    val syncRepository: SyncRepository
    val locationService: LocationService
    val connectivityMonitor: NetworkConnectivityMonitor
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val database = AgriScoutDatabase.getInstance(context)
    private val firebaseApp = runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
    private val firebaseAuth = firebaseApp?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }
    private val firestore = firebaseApp?.let { runCatching { FirebaseFirestore.getInstance(it) }.getOrNull() }
    private val firebaseMessaging = firebaseApp?.let { runCatching { FirebaseMessaging.getInstance() }.getOrNull() }
    private val storage = firebaseApp?.let { runCatching { FirebaseStorage.getInstance(it) }.getOrNull() }
    private val authService = FirebaseAuthService(firebaseAuth, firestore)
    private val catalogRemoteService = CatalogRemoteService(firestore)
    private val syncRemoteService = SyncRemoteService(firestore, storage)
    private val fcmRemoteService = FcmRemoteService(firestore)
    private val weatherRemoteService = WeatherRemoteService(BuildConfig.WEATHER_API_KEY)
    private val detectionEngine = RuleBasedDetectionEngine()
    private val imageDiseaseClassifier = ImageDiseaseClassifier(context.applicationContext)
    private val detectionFusionEngine = DetectionFusionEngine()
    private val simulatedIoTDataSource = SimulatedIoTDataSource()

    override val authRepository = AuthRepository(authService)
    override val farmRepository = FarmRepository(
        farmDao = database.farmDao(),
        authService = authService,
        remoteService = syncRemoteService
    )
    override val reportRepository = ReportRepository(
        reportDao = database.fieldReportDao(),
        authService = authService,
        remoteService = syncRemoteService
    )
    override val farmVisitRepository = FarmVisitRepository(
        farmVisitDao = database.farmVisitDao()
    )
    override val sensorRepository = SensorRepository(
        sensorReadingDao = database.sensorReadingDao(),
        iotDataSource = simulatedIoTDataSource
    )
    override val inventoryRepository = InventoryRepository(
        inventoryRequestDao = database.inventoryRequestDao(),
        inventoryItemDao = database.inventoryItemDao(),
        catalogRemoteService = catalogRemoteService
    )
    override val marketplaceRepository = MarketplaceRepository(
        supplierProductDao = database.supplierProductDao(),
        productRequestDao = database.productRequestDao(),
        harvestListingDao = database.harvestListingDao(),
        harvestRequestDao = database.harvestRequestDao(),
        catalogRemoteService = catalogRemoteService
    )
    override val detectionRepository = DetectionRepository(
        context = context.applicationContext,
        diseaseCatalogDao = database.diseaseCatalogDao(),
        engine = detectionEngine,
        imageClassifier = imageDiseaseClassifier,
        fusionEngine = detectionFusionEngine
    )
    override val cropLifecycleEstimator = CropLifecycleEstimator()
    private val cropCalendarLoader = CropCalendarLoader(context.applicationContext)
    private val cropCalendarEngine = CropCalendarEngine(cropCalendarLoader)
    private val ruleBasedRecommendationEngine = RuleBasedRecommendationEngine()
    override val agronomicOrchestrator = AgronomicOrchestrator(
        calendarEngine = cropCalendarEngine,
        ruleEngine = ruleBasedRecommendationEngine,
        cropLifecycleEstimator = cropLifecycleEstimator
    )
    override val recommendationRepository = RecommendationRepository(
        recommendationDao = database.farmRecommendationDao(),
        inventoryItemDao = database.inventoryItemDao(),
        agronomicOrchestrator = agronomicOrchestrator
    )
    override val catalogRepository = CatalogRepository(
        diseaseCatalogDao = database.diseaseCatalogDao(),
        weatherWarningDao = database.weatherWarningDao(),
        remoteService = catalogRemoteService
    )
    override val weatherRepository = WeatherRepository(
        snapshotDao = database.weatherSnapshotDao(),
        warningDao = database.weatherWarningDao(),
        remoteService = weatherRemoteService
    )
    override val notificationRepository = NotificationRepository(
        authService = authService,
        tokenDao = database.fcmTokenDao(),
        warningDao = database.weatherWarningDao(),
        remoteService = fcmRemoteService,
        firebaseMessaging = firebaseMessaging
    )
    override val connectivityMonitor = NetworkConnectivityMonitor(context.applicationContext)
    override val syncRepository = SyncRepository(
        farmDao = database.farmDao(),
        reportDao = database.fieldReportDao(),
        farmVisitDao = database.farmVisitDao(),
        farmRecommendationDao = database.farmRecommendationDao(),
        inventoryRequestDao = database.inventoryRequestDao(),
        productRequestDao = database.productRequestDao(),
        harvestListingDao = database.harvestListingDao(),
        harvestRequestDao = database.harvestRequestDao(),
        sensorReadingDao = database.sensorReadingDao(),
        authService = authService,
        remoteService = syncRemoteService,
        connectivityChecker = connectivityMonitor
    )
    override val locationService = LocationService(context.applicationContext)
}
