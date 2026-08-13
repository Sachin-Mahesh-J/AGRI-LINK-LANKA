package com.example.agriscout.recommendation

import com.example.agriscout.crop.CropLifecycleEstimate
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.InventoryItemEntity
import com.example.agriscout.data.local.SensorReadingEntity

enum class RecommendationType {
    IRRIGATION,
    FERTILIZER,
    PEST_CONTROL,
    CHEMICAL,
    HARVEST,
    RISK_ALERT,
    GROWTH_STAGE
}

/**
 * Decision-support recommendation.
 * Prefer [productCategory] for category-level advice; inventory item names are optional stock hints.
 */
data class Recommendation(
    val type: RecommendationType,
    val title: String,
    val message: String,
    val priority: String,
    val suggestedItemName: String? = null,
    val alternativeItemName: String? = null,
    val source: String = "calendar",
    val activityId: String? = null,
    val stage: String? = null,
    val dayOfSeason: Int? = null,
    val suggestedQuantity: Double? = null,
    val quantityUnit: String? = null,
    val activityStatus: String? = null,
    val confidence: Int? = null,
    /** Observed problem or signal that triggered this advice. */
    val issueSignal: String? = null,
    /** Agricultural need implied by the issue (e.g. moisture recovery). */
    val agriculturalNeed: String? = null,
    /** Practical action for the officer. */
    val recommendedAction: String? = null,
    /** Category suggestion — not a guaranteed single product. */
    val productCategory: String? = null,
    /** Short why/how explanation for officers and admins. */
    val rationale: String? = null
)

data class RecommendationInputs(
    val farm: FarmEntity,
    val cropLifecycle: CropLifecycleEstimate,
    val sensorReading: SensorReadingEntity?,
    val latestObservationSeverity: String?,
    val inventoryItems: List<InventoryItemEntity> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis()
)

interface RecommendationEngine {
    fun recommend(inputs: RecommendationInputs): List<Recommendation>
}
