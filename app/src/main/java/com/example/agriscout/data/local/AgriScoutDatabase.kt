package com.example.agriscout.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FarmEntity::class,
        FieldReportEntity::class,
        FarmVisitEntity::class,
        FarmRecommendationEntity::class,
        SensorReadingEntity::class,
        InventoryRequestEntity::class,
        InventoryItemEntity::class,
        DiseaseCatalogEntity::class,
        WeatherWarningEntity::class,
        WeatherSnapshotEntity::class,
        FcmTokenEntity::class,
        SupplierProductEntity::class,
        ProductRequestEntity::class,
        HarvestListingEntity::class,
        HarvestRequestEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AgriScoutDatabase : RoomDatabase() {
    abstract fun farmDao(): FarmDao
    abstract fun fieldReportDao(): FieldReportDao
    abstract fun farmVisitDao(): FarmVisitDao
    abstract fun farmRecommendationDao(): FarmRecommendationDao
    abstract fun sensorReadingDao(): SensorReadingDao
    abstract fun inventoryRequestDao(): InventoryRequestDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun diseaseCatalogDao(): DiseaseCatalogDao
    abstract fun weatherWarningDao(): WeatherWarningDao
    abstract fun weatherSnapshotDao(): WeatherSnapshotDao
    abstract fun fcmTokenDao(): FcmTokenDao
    abstract fun supplierProductDao(): SupplierProductDao
    abstract fun productRequestDao(): ProductRequestDao
    abstract fun harvestListingDao(): HarvestListingDao
    abstract fun harvestRequestDao(): HarvestRequestDao

    companion object {
        @Volatile
        private var instance: AgriScoutDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farms ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE farms ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN issueType TEXT")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN detectedIssue TEXT")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN detectionConfidence INTEGER")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN matchedRuleId TEXT")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN recommendation TEXT")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN preventiveMeasures TEXT")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN detectionUpdatedAt INTEGER")
                db.execSQL("ALTER TABLE weather_warnings ADD COLUMN source TEXT NOT NULL DEFAULT 'FIRESTORE'")
                db.execSQL("ALTER TABLE weather_warnings ADD COLUMN actionRoute TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weather_snapshots (
                        id TEXT NOT NULL PRIMARY KEY,
                        locationLabel TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        temperatureCelsius REAL NOT NULL,
                        humidityPercent INTEGER NOT NULL,
                        windSpeedMetersPerSecond REAL NOT NULL,
                        condition TEXT NOT NULL,
                        forecastSummary TEXT NOT NULL,
                        riskSummary TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS fcm_tokens (
                        token TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farms ADD COLUMN plantingDate INTEGER")
                db.execSQL("ALTER TABLE farms ADD COLUMN photoLocalUri TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS farm_visits (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        cropCondition TEXT NOT NULL,
                        photoLocalUri TEXT,
                        latitude REAL,
                        longitude REAL,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING',
                        remoteId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(farmId) REFERENCES farms(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_farm_visits_farmId ON farm_visits(farmId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_farm_visits_userId ON farm_visits(userId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sensor_readings (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        soilMoisturePercent REAL NOT NULL,
                        temperatureCelsius REAL NOT NULL,
                        humidityPercent REAL NOT NULL,
                        lightIntensityLux REAL NOT NULL,
                        waterLevelPercent REAL NOT NULL,
                        status TEXT NOT NULL,
                        recordedAt INTEGER NOT NULL,
                        FOREIGN KEY(farmId) REFERENCES farms(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_readings_farmId ON sensor_readings(farmId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_readings_recordedAt ON sensor_readings(recordedAt)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_requests (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        farmId TEXT,
                        itemType TEXT NOT NULL,
                        quantity TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        status TEXT NOT NULL,
                        availableStock INTEGER NOT NULL,
                        alternativeItem TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING',
                        remoteId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(farmId) REFERENCES farms(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_requests_farmId ON inventory_requests(farmId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_requests_userId ON inventory_requests(userId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sensor_readings ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sensor_readings ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE sensor_readings ADD COLUMN remoteId TEXT")
                db.execSQL("ALTER TABLE sensor_readings ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sensor_readings_userId ON sensor_readings(userId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farms ADD COLUMN assignedDeviceId TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farms ADD COLUMN ownerUserId TEXT")
                db.execSQL("UPDATE farms SET ownerUserId = userId WHERE ownerUserId IS NULL")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE inventory_requests ADD COLUMN inventoryItemId TEXT")
                db.execSQL("ALTER TABLE inventory_requests ADD COLUMN itemName TEXT")
                db.execSQL("ALTER TABLE inventory_requests ADD COLUMN approvalNote TEXT")
                db.execSQL("ALTER TABLE inventory_requests ADD COLUMN reviewedAt INTEGER")
                db.execSQL("ALTER TABLE inventory_requests ADD COLUMN approvedAt INTEGER")
                db.execSQL("ALTER TABLE inventory_requests ADD COLUMN issuedAt INTEGER")
                db.execSQL("ALTER TABLE inventory_requests ADD COLUMN issuedQuantity REAL")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS inventory_items (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        quantity INTEGER NOT NULL,
                        reorderLevel INTEGER NOT NULL,
                        unit TEXT NOT NULL,
                        alternativeItemIds TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farms ADD COLUMN remotePhotoUrl TEXT")
                db.execSQL("ALTER TABLE farm_visits ADD COLUMN remotePhotoUrl TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS farm_recommendations (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        suggestedItemName TEXT,
                        alternativeItemName TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING',
                        remoteId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(farmId) REFERENCES farms(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_farm_recommendations_farmId ON farm_recommendations(farmId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_farm_recommendations_userId ON farm_recommendations(userId)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN source TEXT NOT NULL DEFAULT 'calendar'")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN activityId TEXT")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN stage TEXT")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN dayOfSeason INTEGER")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN suggestedQuantity REAL")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN quantityUnit TEXT")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN activityStatus TEXT")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN confidence INTEGER")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farms ADD COLUMN gpsAccuracyMeters REAL")
                db.execSQL("ALTER TABLE farms ADD COLUMN gpsCapturedAt INTEGER")
                db.execSQL("ALTER TABLE farms ADD COLUMN gpsSource TEXT")

                db.execSQL("ALTER TABLE field_reports ADD COLUMN pestObservations TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN growthStage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN cropConditionDetail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN recommendedActions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN followUpNotes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN gpsAccuracyMeters REAL")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN gpsCapturedAt INTEGER")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN gpsSource TEXT")

                db.execSQL("ALTER TABLE farm_visits ADD COLUMN cropConditionDetail TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE farm_visits ADD COLUMN pestObservations TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE farm_visits ADD COLUMN growthStage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE farm_visits ADD COLUMN recommendedActions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE farm_visits ADD COLUMN followUpNotes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE farm_visits ADD COLUMN gpsAccuracyMeters REAL")
                db.execSQL("ALTER TABLE farm_visits ADD COLUMN gpsCapturedAt INTEGER")
                db.execSQL("ALTER TABLE farm_visits ADD COLUMN gpsSource TEXT")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sensor_readings ADD COLUMN deviceId TEXT")
                db.execSQL(
                    "ALTER TABLE sensor_readings ADD COLUMN source TEXT NOT NULL DEFAULT 'simulated'"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN issueSignal TEXT")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN agriculturalNeed TEXT")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN recommendedAction TEXT")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN productCategory TEXT")
                db.execSQL("ALTER TABLE farm_recommendations ADD COLUMN rationale TEXT")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN detectionExplanation TEXT")
                db.execSQL("ALTER TABLE field_reports ADD COLUMN detectionSource TEXT")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS supplier_products (
                        id TEXT NOT NULL PRIMARY KEY,
                        supplierId TEXT NOT NULL,
                        supplierName TEXT NOT NULL,
                        name TEXT NOT NULL,
                        category TEXT NOT NULL,
                        cropSuitability TEXT NOT NULL DEFAULT '',
                        description TEXT NOT NULL DEFAULT '',
                        unit TEXT NOT NULL DEFAULT 'units',
                        packSize TEXT NOT NULL DEFAULT '',
                        price REAL,
                        availabilityStatus TEXT NOT NULL DEFAULT 'available',
                        active INTEGER NOT NULL DEFAULT 1,
                        verified INTEGER NOT NULL DEFAULT 1,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_supplier_products_category ON supplier_products(category)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_supplier_products_supplierId ON supplier_products(supplierId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS product_requests (
                        id TEXT NOT NULL PRIMARY KEY,
                        userId TEXT NOT NULL,
                        farmId TEXT,
                        recommendationId TEXT,
                        productCategory TEXT NOT NULL,
                        supplierProductId TEXT NOT NULL,
                        supplierId TEXT NOT NULL,
                        productName TEXT NOT NULL,
                        supplierName TEXT NOT NULL,
                        quantity TEXT NOT NULL,
                        unit TEXT NOT NULL DEFAULT 'units',
                        issueSignal TEXT,
                        agriculturalNeed TEXT,
                        recommendedAction TEXT,
                        rationale TEXT,
                        status TEXT NOT NULL DEFAULT 'created',
                        supplierNote TEXT,
                        adminNote TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING',
                        remoteId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_product_requests_userId ON product_requests(userId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_product_requests_farmId ON product_requests(farmId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_product_requests_supplierProductId ON product_requests(supplierProductId)"
                )
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS harvest_listings (
                        id TEXT NOT NULL PRIMARY KEY,
                        farmId TEXT NOT NULL,
                        farmPath TEXT NOT NULL DEFAULT '',
                        farmName TEXT NOT NULL DEFAULT '',
                        officerUid TEXT NOT NULL DEFAULT '',
                        cropType TEXT NOT NULL,
                        locationText TEXT NOT NULL DEFAULT '',
                        district TEXT NOT NULL DEFAULT '',
                        estimatedQuantityMin REAL,
                        estimatedQuantityMax REAL,
                        quantityUnit TEXT NOT NULL DEFAULT 'tonnes',
                        harvestWindowStartDay INTEGER,
                        harvestWindowEndDay INTEGER,
                        harvestPeriodLabel TEXT NOT NULL DEFAULT '',
                        qualityNote TEXT NOT NULL DEFAULT '',
                        confidence INTEGER,
                        reliabilityLabel TEXT NOT NULL DEFAULT '',
                        predictionSource TEXT NOT NULL DEFAULT 'heuristic',
                        sourceRecommendationId TEXT,
                        listingOrigin TEXT NOT NULL DEFAULT 'prediction',
                        status TEXT NOT NULL DEFAULT 'listed',
                        visibility TEXT NOT NULL DEFAULT 'public',
                        active INTEGER NOT NULL DEFAULT 1,
                        verified INTEGER NOT NULL DEFAULT 0,
                        adminNote TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'PENDING',
                        remoteId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_harvest_listings_farmId ON harvest_listings(farmId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_harvest_listings_cropType ON harvest_listings(cropType)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_harvest_listings_status ON harvest_listings(status)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS harvest_requests (
                        id TEXT NOT NULL PRIMARY KEY,
                        harvestListingId TEXT NOT NULL,
                        buyerId TEXT NOT NULL,
                        buyerUid TEXT NOT NULL DEFAULT '',
                        buyerName TEXT NOT NULL DEFAULT '',
                        farmId TEXT,
                        farmPath TEXT NOT NULL DEFAULT '',
                        farmName TEXT NOT NULL DEFAULT '',
                        cropType TEXT NOT NULL DEFAULT '',
                        requestedQuantity TEXT NOT NULL DEFAULT '',
                        quantityUnit TEXT NOT NULL DEFAULT 'tonnes',
                        message TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'requested',
                        buyerNote TEXT,
                        adminNote TEXT,
                        officerNote TEXT,
                        syncStatus TEXT NOT NULL DEFAULT 'SYNCED',
                        remoteId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_harvest_requests_farmId ON harvest_requests(farmId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_harvest_requests_harvestListingId ON harvest_requests(harvestListingId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_harvest_requests_status ON harvest_requests(status)"
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farms ADD COLUMN assignedCameraDeviceId TEXT")
            }
        }

        fun getInstance(context: Context): AgriScoutDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AgriScoutDatabase::class.java,
                    "agri_scout.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
