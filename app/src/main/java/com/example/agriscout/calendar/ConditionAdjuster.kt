package com.example.agriscout.calendar

import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.WeatherSnapshotEntity

data class ConditionContext(
    val sensorReading: SensorReadingEntity?,
    val weatherSnapshot: WeatherSnapshotEntity?,
    val latestObservationSeverity: String?
)

object ConditionAdjuster {
    fun adjust(
        activity: CalendarActivityTemplate,
        status: ActivityStatus,
        context: ConditionContext
    ): Pair<ActivityStatus, String> {
        val sensor = context.sensorReading
        val notes = mutableListOf<String>()
        var adjustedStatus = status

        activity.minSoilMoisture?.let { minimum ->
            val moisture = sensor?.soilMoisturePercent
            if (moisture != null && moisture < minimum) {
                adjustedStatus = ActivityStatus.BLOCKED
                notes += "Blocked: soil moisture ${moisture.toInt()}% is below ${minimum.toInt()}%. Irrigate first."
            }
        }

        activity.maxTemperatureCelsius?.let { maximum ->
            val temperature = sensor?.temperatureCelsius ?: context.weatherSnapshot?.temperatureCelsius
            if (temperature != null && temperature > maximum) {
                if (activity.type == "CHEMICAL" || activity.productCategory.equals("Chemicals", ignoreCase = true)) {
                    adjustedStatus = ActivityStatus.BLOCKED
                    notes += "Postponed: temperature ${temperature.toInt()}°C exceeds safe spraying limit."
                } else {
                    notes += "Heat stress risk at ${temperature.toInt()}°C. Inspect crop before proceeding."
                }
            }
        }

        if (sensor != null && (sensor.soilMoisturePercent < 18.0 || sensor.waterLevelPercent < 12.0)) {
            if (activity.type == "FERTILIZER") {
                adjustedStatus = ActivityStatus.BLOCKED
                notes += "Blocked: low moisture may reduce fertilizer uptake."
            }
        }

        context.weatherSnapshot?.riskSummary?.takeIf { it.isNotBlank() }?.let { risk ->
            if ("rain" in risk.lowercase() && (activity.type == "CHEMICAL" || activity.productCategory.equals("Chemicals", ignoreCase = true))) {
                adjustedStatus = ActivityStatus.BLOCKED
                notes += "Postponed: weather risk indicates rain — delay chemical application."
            }
        }

        context.latestObservationSeverity?.lowercase()?.let { severity ->
            if (("critical" in severity || "high" in severity) && activity.type == "GROWTH_STAGE") {
                notes += "Latest field observation is high severity — escalate scouting."
            }
        }

        val message = buildString {
            if (activity.notes.isNotBlank()) append(activity.notes)
            if (notes.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append(notes.joinToString(" "))
            }
        }
        return adjustedStatus to message
    }
}
