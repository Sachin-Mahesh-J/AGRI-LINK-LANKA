package com.example.agriscout

import com.example.agriscout.calendar.AgronomicContext
import com.example.agriscout.calendar.AgronomicOrchestrator
import com.example.agriscout.calendar.CalendarActivityTemplate
import com.example.agriscout.calendar.ConditionContext
import com.example.agriscout.calendar.CropCalendarEngine
import com.example.agriscout.calendar.CropCalendarProfile
import com.example.agriscout.calendar.CropProfileProvider
import com.example.agriscout.calendar.CropStageSchedule
import com.example.agriscout.calendar.DiseaseWatchWindow
import com.example.agriscout.calendar.DoseCalculator
import com.example.agriscout.calendar.HarvestWindow
import com.example.agriscout.calendar.TriggerActivityTemplate
import com.example.agriscout.crop.CropLifecycleEstimator
import com.example.agriscout.crop.CropStage
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.InventoryItemEntity
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.simulation.SensorStatus
import com.example.agriscout.recommendation.RecommendationType
import com.example.agriscout.recommendation.RuleBasedRecommendationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class CropCalendarEngineTest {
    private val engine = CropCalendarEngine(TestCropProfileProvider())

    @Test
    fun doseCalculatorConvertsAcresToTotalQuantity() {
        assertEquals(80.0, DoseCalculator.totalQuantity(40.0, "2 acres")!!, 0.01)
    }

    @Test
    fun riceTilleringStageIncludesNitrogenTopDressing() {
        val farm = sampleFarm(ageDays = 32)
        val activities = engine.dueActivities(
            farm = farm,
            ageDays = 32,
            currentStage = CropStage.VEGETATIVE,
            inventoryItems = sampleInventory(),
            context = ConditionContext(null, null, null)
        )

        assertTrue(activities.any { it.activityId == "rice-n-topdress-1" && it.status.name == "DUE" })
    }

    @Test
    fun lowSoilMoistureBlocksFertilizerActivity() {
        val farm = sampleFarm(ageDays = 32)
        val activities = engine.dueActivities(
            farm = farm,
            ageDays = 32,
            currentStage = CropStage.VEGETATIVE,
            inventoryItems = sampleInventory(),
            context = ConditionContext(drySensor(), null, null)
        )

        val topDress = activities.first { it.activityId == "rice-n-topdress-1" }
        assertEquals("BLOCKED", topDress.status.name)
        assertTrue(topDress.message.contains("Irrigate first", ignoreCase = true))
    }

    @Test
    fun detectedIssueTriggersTreatmentActivity() {
        val farm = sampleFarm(ageDays = 70)
        val triggered = engine.triggeredTreatments(
            farm = farm,
            ageDays = 70,
            detectedIssueId = "rice-blast",
            inventoryItems = sampleInventory(),
            context = ConditionContext(null, null, "High")
        )

        assertTrue(triggered.any { it.activityId == "rice-blast-treatment" })
    }

    @Test
    fun agronomicOrchestratorMergesCalendarAndRuleRecommendations() {
        val orchestrator = AgronomicOrchestrator(
            calendarEngine = engine,
            ruleEngine = RuleBasedRecommendationEngine(),
            cropLifecycleEstimator = CropLifecycleEstimator()
        )
        val farm = sampleFarm(ageDays = 32)
        val recommendations = orchestrator.recommend(
            AgronomicContext(
                farm = farm,
                lifecycle = CropLifecycleEstimator().estimate(farm.cropType, farm.plantingDate, NOW),
                sensorReading = drySensor(),
                weatherSnapshot = null,
                latestObservationSeverity = "High",
                detectedIssueId = null,
                inventoryItems = sampleInventory()
            )
        )

        assertTrue(recommendations.any { it.type == RecommendationType.FERTILIZER && it.source == "calendar" })
        assertTrue(recommendations.any { it.type == RecommendationType.IRRIGATION })
        assertTrue(recommendations.any { !it.rationale.isNullOrBlank() || !it.agriculturalNeed.isNullOrBlank() })
        assertTrue(recommendations.any { it.confidence != null && it.confidence!! > 0 })
    }

    @Test
    fun harvestOutlookAppearsBeforeWindowWithHeuristicSource() {
        val farm = sampleFarm(ageDays = 90)
        val activities = engine.dueActivities(
            farm = farm,
            ageDays = 90,
            currentStage = CropStage.FLOWERING,
            inventoryItems = sampleInventory(),
            context = ConditionContext(null, null, "Medium")
        )

        val harvest = activities.first { it.activityId == "rice-harvest-forecast" }
        assertEquals("heuristic", harvest.source)
        assertEquals("UPCOMING", harvest.status.name)
        assertTrue(harvest.message.contains("Estimate only", ignoreCase = true))
        assertTrue(harvest.confidence != null && harvest.confidence!! >= 25)
        assertEquals("Harvest planning and yield estimation", harvest.agriculturalNeed)
    }

    private class TestCropProfileProvider : CropProfileProvider {
        override fun loadProfile(cropType: String): CropCalendarProfile = buildRiceProfile()
    }

    private fun sampleFarm(ageDays: Long): FarmEntity = FarmEntity(
        id = "farm-1",
        userId = "officer-1",
        farmName = "East Field",
        farmerName = "Asha",
        cropType = "Rice",
        locationText = "Satara",
        landSize = "2 acres",
        notes = "",
        plantingDate = NOW - TimeUnit.DAYS.toMillis(ageDays),
        createdAt = NOW,
        updatedAt = NOW
    )

    private fun sampleInventory() = listOf(
        InventoryItemEntity("fert-1", "Urea", "Fertilizers", 120, 10, "kg", "", 1L),
        InventoryItemEntity("chem-1", "Triazole fungicide", "Chemicals", 8, 2, "L", "", 1L)
    )

    private fun drySensor() = SensorReadingEntity(
        id = "sensor-1",
        farmId = "farm-1",
        userId = "officer-1",
        soilMoisturePercent = 15.0,
        temperatureCelsius = 30.0,
        humidityPercent = 55.0,
        lightIntensityLux = 50_000.0,
        waterLevelPercent = 20.0,
        status = SensorStatus.WARNING,
        recordedAt = NOW
    )

    private companion object {
        const val NOW = 2_000_000_000_000L

        fun buildRiceProfile(): CropCalendarProfile = CropCalendarProfile(
            cropId = "rice",
            displayName = "Rice",
            stages = listOf(
                CropStageSchedule(
                    stage = "VEGETATIVE",
                    dayRange = 16..45,
                    activities = listOf(
                        CalendarActivityTemplate(
                            id = "rice-n-topdress-1",
                            type = "FERTILIZER",
                            title = "1st nitrogen top dressing",
                            productCategory = "Fertilizers",
                            preferredProducts = listOf("Urea"),
                            dosePerAcreKg = 40.0,
                            unit = "kg",
                            minSoilMoisture = 25.0,
                            notes = "Apply at tillering."
                        )
                    )
                )
            ),
            diseaseWatchWindows = listOf(
                DiseaseWatchWindow(
                    issueId = "rice-blast",
                    stage = "FLOWERING",
                    dayRange = 40..95,
                    triggerActivity = TriggerActivityTemplate(
                        id = "rice-blast-treatment",
                        type = "CHEMICAL",
                        title = "Rice blast treatment",
                        productCategory = "Chemicals",
                        preferredProducts = listOf("Triazole fungicide"),
                        doseNote = "Apply per label."
                    )
                )
            ),
            harvestWindow = HarvestWindow(dayRange = 105..130, yieldPerAcreTonnesBaseline = 1.5)
        )
    }
}
