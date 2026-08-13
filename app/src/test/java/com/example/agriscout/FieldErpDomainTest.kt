package com.example.agriscout

import com.example.agriscout.crop.CropLifecycleEstimator
import com.example.agriscout.crop.CropStage
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.InventoryItemEntity
import com.example.agriscout.data.local.InventoryRequestEntity
import com.example.agriscout.data.remote.CatalogRemoteService
import com.example.agriscout.data.repository.InventoryRepository
import com.example.agriscout.data.simulation.SensorStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.example.agriscout.data.simulation.SimulatedIoTDataSource
import com.example.agriscout.recommendation.RecommendationInputs
import com.example.agriscout.recommendation.RecommendationType
import com.example.agriscout.recommendation.RuleBasedRecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class FieldErpDomainTest {
    @Test
    fun cropLifecycleEstimatorUsesPlantingAge() {
        val now = NOW
        val plantingDate = now - TimeUnit.DAYS.toMillis(70)
        val estimate = CropLifecycleEstimator().estimate("Rice", plantingDate, now)

        assertEquals(CropStage.FLOWERING, estimate.stage)
        assertEquals(70L, estimate.ageDays)
    }

    @Test
    fun simulatedSensorClassifierMarksCriticalMoistureAndWaterLevel() {
        val dataSource = SimulatedIoTDataSource()

        assertEquals(
            SensorStatus.CRITICAL,
            dataSource.classify(
                soilMoisturePercent = 12.0,
                temperatureCelsius = 30.0,
                humidityPercent = 60.0,
                waterLevelPercent = 8.0
            )
        )
    }

    @Test
    fun simulatedReadingStampsSourceAndAssignedDevice() {
        val farm = sampleFarm().copy(assignedDeviceId = "ESP32-FARM-001")
        val reading = SimulatedIoTDataSource().generateReading(farm, NOW)

        assertEquals("simulated", reading.source)
        assertEquals("ESP32-FARM-001", reading.deviceId)
        assertEquals(farm.id, reading.farmId)
    }

    @Test
    fun ruleBasedRecommendationEnginePrioritizesIrrigationAndFloweringNutrition() {
        val farm = sampleFarm()
        val lifecycle = CropLifecycleEstimator().estimate("Rice", farm.plantingDate, NOW)
        val recommendations = RuleBasedRecommendationEngine().recommend(
            RecommendationInputs(
                farm = farm,
                cropLifecycle = lifecycle,
                sensorReading = SensorReadingEntity(
                    id = "sensor-1",
                    farmId = farm.id,
                    userId = farm.userId,
                    soilMoisturePercent = 20.0,
                    temperatureCelsius = 36.0,
                    humidityPercent = 55.0,
                    lightIntensityLux = 50_000.0,
                    waterLevelPercent = 20.0,
                    status = SensorStatus.WARNING,
                    recordedAt = NOW
                ),
                latestObservationSeverity = "High",
                nowMillis = NOW
            )
        )

        assertTrue(recommendations.any { it.type == RecommendationType.IRRIGATION && it.priority == "High" })
        assertTrue(recommendations.any { it.type == RecommendationType.FERTILIZER && "Flowering" in it.title })
        assertTrue(recommendations.any { it.type == RecommendationType.PEST_CONTROL })
        val irrigation = recommendations.first { it.type == RecommendationType.IRRIGATION }
        assertEquals("Irrigation", irrigation.productCategory)
        assertTrue(!irrigation.rationale.isNullOrBlank())
        assertTrue(irrigation.confidence != null && irrigation.confidence!! >= 70)
    }

    @Test
    fun ruleBasedRecommendationEngineUsesInventoryHintsForFertilizerAndPestControl() {
        val farm = sampleFarm()
        val lifecycle = CropLifecycleEstimator().estimate("Rice", farm.plantingDate, NOW)
        val inventory = listOf(
            InventoryItemEntity("fert-1", "Urea", "Fertilizers", 12, 5, "bags", "fert-2", 1L),
            InventoryItemEntity("fert-2", "Potassium blend", "Fertilizers", 8, 3, "bags", "", 1L),
            InventoryItemEntity("chem-1", "Neem oil", "Chemicals", 6, 2, "L", "", 1L)
        )
        val recommendations = RuleBasedRecommendationEngine().recommend(
            RecommendationInputs(
                farm = farm,
                cropLifecycle = lifecycle,
                sensorReading = SensorReadingEntity(
                    id = "sensor-1",
                    farmId = farm.id,
                    userId = farm.userId,
                    soilMoisturePercent = 20.0,
                    temperatureCelsius = 36.0,
                    humidityPercent = 55.0,
                    lightIntensityLux = 50_000.0,
                    waterLevelPercent = 20.0,
                    status = SensorStatus.WARNING,
                    recordedAt = NOW
                ),
                latestObservationSeverity = "High",
                inventoryItems = inventory
            )
        )

        val fertilizer = recommendations.first { it.type == RecommendationType.FERTILIZER }
        assertEquals("Urea", fertilizer.suggestedItemName)
        assertEquals("Potassium blend", fertilizer.alternativeItemName)
        val pest = recommendations.first { it.type == RecommendationType.PEST_CONTROL }
        assertEquals("Neem oil", pest.suggestedItemName)
    }

    @Test
    fun inventoryRepositorySuggestsAlternativeWhenSelectedItemIsOutOfStock() {
        val repository = InventoryRepository(
            inventoryRequestDao = EmptyInventoryRequestDao(),
            inventoryItemDao = EmptyInventoryItemDao(),
            catalogRemoteService = CatalogRemoteService(null)
        )

        val stock = repository.stockFor(
            itemType = "Seeds",
            inventoryItemId = "seed-1",
            cachedItems = listOf(
                InventoryItemEntity("seed-1", "Hybrid Rice Seed", "Seeds", 0, 10, "kg", "seed-2", 1L),
                InventoryItemEntity("seed-2", "Certified hybrid seed lot B", "Seeds", 15, 5, "kg", "", 1L)
            )
        )

        assertEquals("Certified hybrid seed lot B", stock.alternativeItem)
        assertEquals(0, stock.availableStock)
        assertTrue(stock.isLiveData)
    }

    private class EmptyInventoryRequestDao : com.example.agriscout.data.local.InventoryRequestDao {
        override fun observeRequests(userId: String): Flow<List<InventoryRequestEntity>> = flowOf(emptyList())
        override suspend fun getRequest(id: String): InventoryRequestEntity? = null
        override suspend fun pendingRequests(userId: String, synced: String): List<InventoryRequestEntity> = emptyList()
        override suspend fun upsert(request: InventoryRequestEntity) = Unit
        override suspend fun updateStatus(requestId: String, status: String, updatedAt: Long, pending: String) = Unit
        override suspend fun claimLegacyRequests(legacyUserId: String, newUserId: String, updatedAt: Long, pending: String) = Unit
    }

    private class EmptyInventoryItemDao : com.example.agriscout.data.local.InventoryItemDao {
        override fun observeItems(): Flow<List<InventoryItemEntity>> = flowOf(emptyList())
        override suspend fun getItems(): List<InventoryItemEntity> = emptyList()
        override suspend fun upsertAll(items: List<InventoryItemEntity>) = Unit
        override suspend fun clearAll() = Unit
    }

    private fun sampleFarm() = FarmEntity(
        id = "farm-1",
        userId = "officer-1",
        farmName = "East Field",
        farmerName = "Asha",
        cropType = "Rice",
        locationText = "Satara",
        landSize = "2 acres",
        notes = "",
        plantingDate = NOW - TimeUnit.DAYS.toMillis(70),
        createdAt = NOW,
        updatedAt = NOW
    )

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}
