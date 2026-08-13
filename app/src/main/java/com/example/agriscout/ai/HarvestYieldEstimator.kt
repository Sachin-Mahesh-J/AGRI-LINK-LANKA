package com.example.agriscout.ai

import com.example.agriscout.calendar.CropCalendarProfile
import com.example.agriscout.calendar.DoseCalculator
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.SensorReadingEntity
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.round

/**
 * Structured harvest-window and yield heuristic.
 * Designed as a replaceable Layer-2 estimator (not a trained ML model).
 * Outputs are decision-support estimates with explicit uncertainty.
 */
data class HarvestYieldEstimate(
    val windowStartDay: Int,
    val windowEndDay: Int,
    val ageDays: Int,
    val phase: HarvestPhase,
    val estimatedYieldMinTonnes: Double,
    val estimatedYieldMaxTonnes: Double,
    val confidence: Int,
    val reliabilityLabel: String,
    val rationale: String,
    val uncertaintyNotes: String,
    val source: String = "heuristic"
)

enum class HarvestPhase {
    BEFORE_WINDOW,
    IN_WINDOW,
    AFTER_WINDOW,
    UNKNOWN
}

class HarvestYieldEstimator {
    fun estimate(
        profile: CropCalendarProfile,
        farm: FarmEntity,
        ageDays: Long?,
        latestObservationSeverity: String? = null,
        sensorReading: SensorReadingEntity? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): HarvestYieldEstimate? {
        if (ageDays == null) return null

        val window = profile.harvestWindow
        val age = ageDays.toInt()
        val acres = DoseCalculator.parseAcres(farm.landSize)
        val baselinePerAcre = window.yieldPerAcreTonnesBaseline
        val stressFactor = stressFactor(latestObservationSeverity, sensorReading)
        val adjustedPerAcre = baselinePerAcre * stressFactor
        val estimatedMin = round1(adjustedPerAcre * acres * 0.85)
        val estimatedMax = round1(adjustedPerAcre * acres * 1.15)

        val phase = when {
            age < window.dayRange.first -> HarvestPhase.BEFORE_WINDOW
            age > window.dayRange.last -> HarvestPhase.AFTER_WINDOW
            else -> HarvestPhase.IN_WINDOW
        }

        val confidence = confidenceScore(
            age = age,
            windowStart = window.dayRange.first,
            windowEnd = window.dayRange.last,
            severityKnown = !latestObservationSeverity.isNullOrBlank(),
            sensor = sensorReading,
            nowMillis = nowMillis
        )

        val rationale = buildString {
            append("Calendar baseline ${round1(baselinePerAcre)} t/acre for ${profile.displayName}")
            append(" × ${farm.landSize.ifBlank { "1 acre" }}")
            if (stressFactor < 0.99) {
                append("; adjusted ${((1.0 - stressFactor) * 100).toInt()}% for field stress signals")
            } else if (stressFactor > 1.01) {
                append("; slight uplift from favorable sensor readings")
            }
            append(". Source: heuristic (calendar + observations), not a guaranteed forecast.")
        }

        val uncertainty = buildString {
            append("Estimate only — actual harvest depends on variety, weather, pests, and field management. ")
            when (phase) {
                HarvestPhase.BEFORE_WINDOW ->
                    append("Crop is ${window.dayRange.first - age} day(s) before the typical harvest window.")
                HarvestPhase.IN_WINDOW ->
                    append("Crop age is inside the typical harvest window; confirm grain/fruit maturity on site.")
                HarvestPhase.AFTER_WINDOW ->
                    append("Typical harvest window may have closed; verify leftover crop and storage readiness.")
                HarvestPhase.UNKNOWN -> Unit
            }
        }

        return HarvestYieldEstimate(
            windowStartDay = window.dayRange.first,
            windowEndDay = window.dayRange.last,
            ageDays = age,
            phase = phase,
            estimatedYieldMinTonnes = estimatedMin,
            estimatedYieldMaxTonnes = estimatedMax,
            confidence = confidence,
            reliabilityLabel = reliabilityLabel(confidence),
            rationale = rationale,
            uncertaintyNotes = uncertainty.trim(),
            source = "heuristic"
        )
    }

    private fun stressFactor(severity: String?, sensor: SensorReadingEntity?): Double {
        var factor = 1.0
        val normalized = severity?.lowercase(Locale.getDefault()).orEmpty()
        factor *= when {
            "critical" in normalized -> 0.75
            "high" in normalized -> 0.85
            "medium" in normalized -> 0.92
            else -> 1.0
        }
        if (sensor != null) {
            if (sensor.soilMoisturePercent < 25.0 || sensor.waterLevelPercent < 20.0) factor *= 0.92
            if (sensor.temperatureCelsius > 35.0) factor *= 0.95
            if (sensor.humidityPercent > 85.0) factor *= 0.97
            if (
                sensor.soilMoisturePercent in 35.0..70.0 &&
                sensor.temperatureCelsius in 22.0..32.0 &&
                sensor.waterLevelPercent >= 30.0
            ) {
                factor *= 1.03
            }
        }
        return factor.coerceIn(0.55, 1.15)
    }

    private fun confidenceScore(
        age: Int,
        windowStart: Int,
        windowEnd: Int,
        severityKnown: Boolean,
        sensor: SensorReadingEntity?,
        nowMillis: Long
    ): Int {
        var score = 45
        val mid = (windowStart + windowEnd) / 2
        val distance = kotlin.math.abs(age - mid)
        score += when {
            age in windowStart..windowEnd -> 25
            distance <= 15 -> 15
            distance <= 30 -> 8
            else -> 0
        }
        if (severityKnown) score += 8
        if (sensor != null) {
            score += 10
            val ageMinutes = TimeUnit.MILLISECONDS.toMinutes(nowMillis - sensor.recordedAt)
            if (ageMinutes > 15) score -= 8
        }
        return score.coerceIn(25, 88)
    }

    private fun reliabilityLabel(confidence: Int): String = when {
        confidence >= 70 -> "Moderate"
        confidence >= 50 -> "Limited"
        else -> "Low"
    }

    private fun round1(value: Double): Double = round(value * 10.0) / 10.0
}

fun HarvestYieldEstimate.toOfficerMessage(): String = buildString {
    append(
        when (phase) {
            HarvestPhase.BEFORE_WINDOW ->
                "Estimated harvest window: day $windowStartDay–$windowEndDay (in ${max(0, windowStartDay - ageDays)} day(s)). "
            HarvestPhase.IN_WINDOW ->
                "Harvest window is open (day $ageDays of $windowStartDay–$windowEndDay). "
            HarvestPhase.AFTER_WINDOW ->
                "Typical harvest window was day $windowStartDay–$windowEndDay; crop is now day $ageDays. "
            HarvestPhase.UNKNOWN ->
                "Harvest timing estimate unavailable. "
        }
    )
    append("Estimated quantity: $estimatedYieldMinTonnes–$estimatedYieldMaxTonnes tonnes. ")
    append("Reliability: $reliabilityLabel ($confidence%). ")
    append(uncertaintyNotes)
}
