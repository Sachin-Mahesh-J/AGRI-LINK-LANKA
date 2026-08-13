package com.example.agriscout

import com.example.agriscout.ai.HarvestPhase
import com.example.agriscout.ai.HarvestYieldEstimator
import com.example.agriscout.calendar.CropCalendarProfile
import com.example.agriscout.calendar.HarvestWindow
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.simulation.SensorStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HarvestYieldEstimatorTest {
    private val estimator = HarvestYieldEstimator()
    private val profile = CropCalendarProfile(
        cropId = "rice",
        displayName = "Rice",
        stages = emptyList(),
        diseaseWatchWindows = emptyList(),
        harvestWindow = HarvestWindow(dayRange = 105..130, yieldPerAcreTonnesBaseline = 1.5)
    )

    @Test
    fun estimatesYieldUsingBaselineAcresAndStress() {
        val farm = sampleFarm(landSize = "2 acres")
        val estimate = estimator.estimate(
            profile = profile,
            farm = farm,
            ageDays = 110,
            latestObservationSeverity = "High",
            sensorReading = dryHotSensor()
        )

        assertNotNull(estimate)
        assertEquals(HarvestPhase.IN_WINDOW, estimate!!.phase)
        assertTrue(estimate.estimatedYieldMinTonnes < 1.5 * 2 * 0.85)
        assertTrue(estimate.confidence in 25..88)
        assertTrue(estimate.uncertaintyNotes.contains("Estimate only", ignoreCase = true))
        assertEquals("heuristic", estimate.source)
    }

    @Test
    fun reportsUpcomingWindowBeforeHarvestDays() {
        val estimate = estimator.estimate(
            profile = profile,
            farm = sampleFarm(),
            ageDays = 90,
            latestObservationSeverity = null,
            sensorReading = null
        )

        assertEquals(HarvestPhase.BEFORE_WINDOW, estimate!!.phase)
        assertEquals(105, estimate.windowStartDay)
        assertEquals(130, estimate.windowEndDay)
        assertTrue(estimate.rationale.contains("baseline", ignoreCase = true))
    }

    private fun sampleFarm(landSize: String = "2 acres") = FarmEntity(
        id = "farm-1",
        userId = "officer-1",
        farmName = "East Field",
        farmerName = "Asha",
        cropType = "Rice",
        locationText = "Satara",
        landSize = landSize,
        notes = "",
        plantingDate = 1L,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun dryHotSensor() = SensorReadingEntity(
        id = "sensor-1",
        farmId = "farm-1",
        userId = "officer-1",
        soilMoisturePercent = 20.0,
        temperatureCelsius = 37.0,
        humidityPercent = 55.0,
        lightIntensityLux = 50_000.0,
        waterLevelPercent = 18.0,
        status = SensorStatus.WARNING,
        recordedAt = System.currentTimeMillis()
    )
}
