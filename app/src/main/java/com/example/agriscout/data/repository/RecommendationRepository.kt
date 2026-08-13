package com.example.agriscout.data.repository

import com.example.agriscout.calendar.AgronomicContext
import com.example.agriscout.calendar.AgronomicOrchestrator
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.FarmRecommendationDao
import com.example.agriscout.data.local.FarmRecommendationEntity
import com.example.agriscout.data.local.InventoryItemDao
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.SyncStatus
import com.example.agriscout.data.local.WeatherSnapshotEntity
import com.example.agriscout.recommendation.Recommendation
import com.example.agriscout.recommendation.RecommendationType
import kotlinx.coroutines.flow.Flow
import java.util.Locale

class RecommendationRepository(
    private val recommendationDao: FarmRecommendationDao,
    private val inventoryItemDao: InventoryItemDao,
    private val agronomicOrchestrator: AgronomicOrchestrator
) {
    fun observeForFarm(farmId: String): Flow<List<FarmRecommendationEntity>> =
        recommendationDao.observeForFarm(farmId)

    suspend fun refreshForFarm(
        userId: String,
        farm: FarmEntity,
        sensorReading: SensorReadingEntity?,
        weatherSnapshot: WeatherSnapshotEntity?,
        latestObservationSeverity: String?,
        detectedIssueId: String?
    ): List<FarmRecommendationEntity> {
        val inventoryItems = inventoryItemDao.getItems()
        val lifecycle = agronomicOrchestrator.lifecycleFor(farm)
        val recommendations = agronomicOrchestrator.recommend(
            AgronomicContext(
                farm = farm,
                lifecycle = lifecycle,
                sensorReading = sensorReading,
                weatherSnapshot = weatherSnapshot,
                latestObservationSeverity = latestObservationSeverity,
                detectedIssueId = detectedIssueId,
                inventoryItems = inventoryItems
            )
        )
        val now = System.currentTimeMillis()
        val entities = recommendations.map { recommendation ->
            FarmRecommendationEntity(
                id = recommendationDocumentId(userId, farm.id, recommendation),
                farmId = farm.id,
                userId = userId,
                type = recommendation.type.name,
                title = recommendation.title,
                message = recommendation.message,
                priority = recommendation.priority,
                suggestedItemName = recommendation.suggestedItemName,
                alternativeItemName = recommendation.alternativeItemName,
                source = recommendation.source,
                activityId = recommendation.activityId,
                stage = recommendation.stage,
                dayOfSeason = recommendation.dayOfSeason,
                suggestedQuantity = recommendation.suggestedQuantity,
                quantityUnit = recommendation.quantityUnit,
                activityStatus = recommendation.activityStatus,
                confidence = recommendation.confidence,
                issueSignal = recommendation.issueSignal,
                agriculturalNeed = recommendation.agriculturalNeed,
                recommendedAction = recommendation.recommendedAction,
                productCategory = recommendation.productCategory,
                rationale = recommendation.rationale,
                syncStatus = SyncStatus.PENDING,
                remoteId = recommendationDocumentId(userId, farm.id, recommendation),
                createdAt = now,
                updatedAt = now
            )
        }
        recommendationDao.deleteForFarm(userId, farm.id)
        recommendationDao.upsertAll(entities)
        return entities
    }
}

fun FarmRecommendationEntity.toRecommendation(): Recommendation = Recommendation(
    type = runCatching { RecommendationType.valueOf(type) }.getOrDefault(RecommendationType.RISK_ALERT),
    title = title,
    message = message,
    priority = priority,
    suggestedItemName = suggestedItemName,
    alternativeItemName = alternativeItemName,
    source = source,
    activityId = activityId,
    stage = stage,
    dayOfSeason = dayOfSeason,
    suggestedQuantity = suggestedQuantity,
    quantityUnit = quantityUnit,
    activityStatus = activityStatus,
    confidence = confidence,
    issueSignal = issueSignal,
    agriculturalNeed = agriculturalNeed,
    recommendedAction = recommendedAction,
    productCategory = productCategory,
    rationale = rationale
)

fun recommendationDocumentId(userId: String, farmId: String, recommendation: Recommendation): String {
    val suffix = recommendation.activityId
        ?: recommendation.type.name.lowercase(Locale.getDefault())
    return "${userId}_${farmId}_$suffix"
}
