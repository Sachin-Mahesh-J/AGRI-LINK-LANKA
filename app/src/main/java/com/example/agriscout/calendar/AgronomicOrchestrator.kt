package com.example.agriscout.calendar

import com.example.agriscout.crop.CropLifecycleEstimate
import com.example.agriscout.crop.CropLifecycleEstimator
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.InventoryItemEntity
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.WeatherSnapshotEntity
import com.example.agriscout.recommendation.Recommendation
import com.example.agriscout.recommendation.RecommendationEngine
import com.example.agriscout.recommendation.RecommendationInputs
import com.example.agriscout.recommendation.RecommendationType
import java.util.Locale

data class AgronomicContext(
    val farm: FarmEntity,
    val lifecycle: CropLifecycleEstimate,
    val sensorReading: SensorReadingEntity?,
    val weatherSnapshot: WeatherSnapshotEntity?,
    val latestObservationSeverity: String?,
    val detectedIssueId: String?,
    val inventoryItems: List<InventoryItemEntity>
)

class AgronomicOrchestrator(
    private val calendarEngine: CropCalendarEngine,
    private val ruleEngine: RecommendationEngine,
    private val cropLifecycleEstimator: CropLifecycleEstimator
) {
    fun recommend(context: AgronomicContext): List<Recommendation> {
        val ageDays = context.lifecycle.ageDays ?: return fallbackOnly(context)
        val conditionContext = ConditionContext(
            sensorReading = context.sensorReading,
            weatherSnapshot = context.weatherSnapshot,
            latestObservationSeverity = context.latestObservationSeverity
        )

        val calendarActivities = calendarEngine.dueActivities(
            farm = context.farm,
            ageDays = ageDays,
            currentStage = context.lifecycle.stage,
            inventoryItems = context.inventoryItems,
            context = conditionContext
        ) + calendarEngine.triggeredTreatments(
            farm = context.farm,
            ageDays = ageDays,
            detectedIssueId = context.detectedIssueId,
            inventoryItems = context.inventoryItems,
            context = conditionContext
        )

        val calendarRecommendations = calendarActivities.map { it.toRecommendation() }
        val ruleRecommendations = ruleEngine.recommend(
            RecommendationInputs(
                farm = context.farm,
                cropLifecycle = context.lifecycle,
                sensorReading = context.sensorReading,
                latestObservationSeverity = context.latestObservationSeverity,
                inventoryItems = context.inventoryItems
            )
        ).map { recommendation ->
            recommendation.copy(source = recommendation.source.takeIf { it.isNotBlank() && it != "calendar" } ?: "rules")
        }

        return mergeRecommendations(calendarRecommendations, ruleRecommendations)
            .sortedByDescending { priorityRank(it.priority) }
    }

    fun lifecycleFor(farm: FarmEntity): CropLifecycleEstimate =
        cropLifecycleEstimator.estimate(farm.cropType, farm.plantingDate)

    private fun fallbackOnly(context: AgronomicContext): List<Recommendation> =
        ruleEngine.recommend(
            RecommendationInputs(
                farm = context.farm,
                cropLifecycle = context.lifecycle,
                sensorReading = context.sensorReading,
                latestObservationSeverity = context.latestObservationSeverity,
                inventoryItems = context.inventoryItems
            )
        ).map { it.copy(source = it.source.takeIf { source -> source.isNotBlank() && source != "calendar" } ?: "rules") }

    private fun mergeRecommendations(
        calendar: List<Recommendation>,
        rules: List<Recommendation>
    ): List<Recommendation> {
        val merged = calendar.toMutableList()
        rules.forEach { rule ->
            val duplicate = merged.any { existing ->
                existing.type == rule.type &&
                    (existing.activityId == null || existing.title.equals(rule.title, ignoreCase = true))
            }
            if (!duplicate) merged += rule
        }
        return merged
    }

    private fun priorityRank(priority: String): Int = when (priority.lowercase(Locale.getDefault())) {
        "critical" -> 5
        "high" -> 4
        "medium" -> 3
        "low" -> 2
        else -> 1
    }
}

private fun ScheduledActivity.toRecommendation(): Recommendation = Recommendation(
    type = runCatching { RecommendationType.valueOf(type) }.getOrDefault(RecommendationType.GROWTH_STAGE),
    title = title,
    message = message,
    priority = priority,
    suggestedItemName = suggestedItemName,
    alternativeItemName = alternativeItemName,
    source = source,
    activityId = activityId,
    stage = stage,
    dayOfSeason = dayOfSeason.toInt(),
    suggestedQuantity = suggestedQuantity,
    quantityUnit = quantityUnit,
    activityStatus = status.name.lowercase(Locale.getDefault()),
    confidence = confidence,
    issueSignal = issueSignal,
    agriculturalNeed = agriculturalNeed,
    recommendedAction = recommendedAction,
    productCategory = productCategory.takeIf { it.isNotBlank() },
    rationale = rationale
)
