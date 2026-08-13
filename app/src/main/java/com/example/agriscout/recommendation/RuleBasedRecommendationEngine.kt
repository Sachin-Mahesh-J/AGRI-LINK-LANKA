package com.example.agriscout.recommendation

import com.example.agriscout.crop.CropStage
import com.example.agriscout.data.local.InventoryItemEntity
import java.util.Locale
import java.util.concurrent.TimeUnit

class RuleBasedRecommendationEngine : RecommendationEngine {
    override fun recommend(inputs: RecommendationInputs): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        val sensor = inputs.sensorReading

        if (sensor == null) {
            recommendations += Recommendation(
                type = RecommendationType.RISK_ALERT,
                title = "Sensor data pending",
                message = "Refresh IoT readings before finalizing field advice. Irrigation and stress alerts need current moisture and temperature.",
                priority = "Low",
                confidence = 40,
                issueSignal = "No sensor reading",
                agriculturalNeed = "Current field environment context",
                recommendedAction = "Open the sensor dashboard and refresh readings",
                productCategory = null,
                rationale = "Decision support is weaker without soil moisture, temperature, or water-level data."
            )
        } else {
            val sensorAgeMinutes = TimeUnit.MILLISECONDS.toMinutes(
                (inputs.nowMillis - sensor.recordedAt).coerceAtLeast(0)
            )
            if (sensorAgeMinutes > STALE_MINUTES) {
                recommendations += Recommendation(
                    type = RecommendationType.RISK_ALERT,
                    title = "Sensor data may be stale",
                    message = "Last reading is about ${sensorAgeMinutes} minutes old. Treat irrigation and stress advice as provisional until refreshed.",
                    priority = "Medium",
                    confidence = 55,
                    issueSignal = "Stale sensor reading (~${sensorAgeMinutes} min)",
                    agriculturalNeed = "Fresh environmental monitoring",
                    recommendedAction = "Refresh live device or simulation before acting",
                    rationale = "Readings older than $STALE_MINUTES minutes can miss rapid moisture or heat changes."
                )
            }

            if (sensor.soilMoisturePercent < 30 || sensor.waterLevelPercent < 25) {
                val critical = sensor.soilMoisturePercent < 18 || sensor.waterLevelPercent < 12
                recommendations += Recommendation(
                    type = RecommendationType.IRRIGATION,
                    title = "Irrigation recommended",
                    message = composeMessage(
                        issue = "Low soil moisture (${sensor.soilMoisturePercent.toInt()}%) or water level (${sensor.waterLevelPercent.toInt()}%)",
                        need = "Restore plant-available water",
                        action = "Schedule irrigation and verify pump availability",
                        category = "Irrigation / water management",
                        why = "Moisture or water-level below rule threshold (30% / 25%)."
                    ),
                    priority = if (critical) "Critical" else "High",
                    confidence = if (critical) 88 else 78,
                    issueSignal = "Soil moisture ${sensor.soilMoisturePercent.toInt()}% · water ${sensor.waterLevelPercent.toInt()}%",
                    agriculturalNeed = "Moisture recovery",
                    recommendedAction = "Schedule irrigation and verify pump availability",
                    productCategory = "Irrigation",
                    rationale = "Rule threshold: soil moisture < 30% or water level < 25%."
                )
            } else {
                recommendations += Recommendation(
                    type = RecommendationType.IRRIGATION,
                    title = "Irrigation normal",
                    message = composeMessage(
                        issue = "Moisture within expected range",
                        need = "Maintain current water balance",
                        action = "Continue routine monitoring",
                        category = "Irrigation / water management",
                        why = "Soil moisture ${sensor.soilMoisturePercent.toInt()}% and water level ${sensor.waterLevelPercent.toInt()}% are above alert thresholds."
                    ),
                    priority = "Low",
                    confidence = 72,
                    issueSignal = "Moisture OK",
                    agriculturalNeed = "Routine water monitoring",
                    recommendedAction = "Continue routine monitoring",
                    productCategory = "Irrigation",
                    rationale = "Sensor values above irrigation alert thresholds."
                )
            }

            if (sensor.temperatureCelsius > 35) {
                recommendations += Recommendation(
                    type = RecommendationType.RISK_ALERT,
                    title = "Heat stress alert",
                    message = composeMessage(
                        issue = "High canopy/air temperature (${sensor.temperatureCelsius.toInt()}°C)",
                        need = "Reduce heat stress and avoid unsafe spraying windows",
                        action = "Inspect for curling/wilting; avoid midday chemical spraying",
                        category = "Environmental risk",
                        why = "Temperature exceeds 35°C heat-stress rule."
                    ),
                    priority = if (sensor.temperatureCelsius > 40) "Critical" else "High",
                    confidence = if (sensor.temperatureCelsius > 40) 90 else 80,
                    issueSignal = "Temperature ${sensor.temperatureCelsius.toInt()}°C",
                    agriculturalNeed = "Heat-stress mitigation",
                    recommendedAction = "Inspect leaves; avoid midday spraying",
                    productCategory = "Environmental management",
                    rationale = "Rule threshold: temperature > 35°C."
                )
            }

            if (sensor.humidityPercent >= 85.0 && sensor.temperatureCelsius >= 28.0) {
                recommendations += Recommendation(
                    type = RecommendationType.RISK_ALERT,
                    title = "Disease-favorable humidity",
                    message = composeMessage(
                        issue = "High humidity (${sensor.humidityPercent.toInt()}%) with warm temperature",
                        need = "Increase disease scouting",
                        action = "Scout for leaf spots/blast symptoms in humid pockets",
                        category = "Crop protection (scouting)",
                        why = "Warm + humid conditions favor fungal disease pressure."
                    ),
                    priority = "Medium",
                    confidence = 68,
                    issueSignal = "Humidity ${sensor.humidityPercent.toInt()}% · temp ${sensor.temperatureCelsius.toInt()}°C",
                    agriculturalNeed = "Early disease detection",
                    recommendedAction = "Increase leaf scouting frequency for 48 hours",
                    productCategory = "Chemicals",
                    rationale = "Heuristic: humidity ≥ 85% and temperature ≥ 28°C."
                )
            }

            if (sensor.lightIntensityLux < 8_000.0) {
                recommendations += Recommendation(
                    type = RecommendationType.RISK_ALERT,
                    title = "Low light warning",
                    message = composeMessage(
                        issue = "Low light intensity (${sensor.lightIntensityLux.toInt()} lux)",
                        need = "Confirm sensor placement / canopy shading",
                        action = "Check if sensor is shaded or weather is heavily overcast",
                        category = "Environmental monitoring",
                        why = "Very low lux can indicate shading, sensor fault, or prolonged cloud cover."
                    ),
                    priority = "Low",
                    confidence = 60,
                    issueSignal = "Light ${sensor.lightIntensityLux.toInt()} lux",
                    agriculturalNeed = "Reliable light/environment signal",
                    recommendedAction = "Verify sensor placement and weather conditions",
                    rationale = "Heuristic: light intensity below 8,000 lux."
                )
            }
        }

        recommendations += stageRecommendation(inputs)
        pestRecommendation(inputs)?.let { recommendations += it }
        return recommendations.sortedByDescending { priorityRank(it.priority) }
    }

    private fun stageRecommendation(inputs: RecommendationInputs): Recommendation {
        val crop = inputs.farm.cropType.ifBlank { "crop" }
        val inventory = inventoryHint("Fertilizers", inputs.inventoryItems)
        val (need, action, title, priority, confidence, rationale) = when (inputs.cropLifecycle.stage) {
            CropStage.GERMINATION -> StageAdvice(
                need = "Steady seedbed moisture without heavy fertilizer",
                action = "Keep seedbed moisture steady and avoid heavy fertilizer until seedlings establish",
                title = "Support germination",
                priority = "Medium",
                confidence = 70,
                rationale = "Lifecycle rule for germination stage (${inputs.cropLifecycle.ageDays ?: "?"} days)."
            )
            CropStage.VEGETATIVE -> StageAdvice(
                need = "Balanced nitrogen if crop is not stressed",
                action = "Consider nitrogen support after field inspection confirms no stress",
                title = "Nitrogen support suggested",
                priority = "Medium",
                confidence = 68,
                rationale = "Vegetative-stage nutrition heuristic for $crop."
            )
            CropStage.FLOWERING -> StageAdvice(
                need = "Potassium and micronutrient support; careful irrigation",
                action = "Prioritize potassium/micronutrients; avoid over-irrigation",
                title = "Flowering-stage nutrition",
                priority = "High",
                confidence = 72,
                rationale = "Flowering-stage nutrition heuristic for $crop."
            )
            CropStage.HARVEST_READY -> StageAdvice(
                need = "Maturity confirmation and harvest logistics",
                action = "Record maturity observations and coordinate harvest resources",
                title = "Prepare harvest assessment",
                priority = "Medium",
                confidence = 65,
                rationale = "Crop age suggests harvest readiness — confirm in field."
            )
            CropStage.UNKNOWN -> StageAdvice(
                need = "Planting date for lifecycle guidance",
                action = "Update farm planting date to enable stage recommendations",
                title = "Add planting date",
                priority = "Low",
                confidence = 35,
                rationale = "Planting date missing; stage cannot be estimated."
            )
        }
        return Recommendation(
            type = RecommendationType.FERTILIZER,
            title = title,
            message = composeMessage(
                issue = "Crop stage: ${inputs.cropLifecycle.stage.label}",
                need = need,
                action = action,
                category = "Fertilizers",
                why = rationale,
                inventory = inventoryMessage(inventory)
            ),
            priority = priority,
            suggestedItemName = inventory.first,
            alternativeItemName = inventory.second,
            confidence = confidence,
            issueSignal = "Stage ${inputs.cropLifecycle.stage.label}",
            agriculturalNeed = need,
            recommendedAction = action,
            productCategory = "Fertilizers",
            rationale = rationale
        )
    }

    private fun pestRecommendation(inputs: RecommendationInputs): Recommendation? {
        val normalized = inputs.latestObservationSeverity?.lowercase(Locale.getDefault()).orEmpty()
        if (normalized.isBlank()) return null
        val inventory = inventoryHint("Chemicals", inputs.inventoryItems)
        val inventoryMessage = inventoryMessage(inventory)
        return when {
            "critical" in normalized || "high" in normalized -> Recommendation(
                type = RecommendationType.PEST_CONTROL,
                title = "Pest or disease escalation",
                message = composeMessage(
                    issue = "Latest observation severity: ${inputs.latestObservationSeverity}",
                    need = "Approved crop-protection response after confirmation",
                    action = "Use approved pesticide guidance and request expert confirmation before treatment",
                    category = "Chemicals / crop protection",
                    why = "High/critical field severity escalates pest or disease risk.",
                    inventory = inventoryMessage
                ),
                priority = "High",
                suggestedItemName = inventory.first,
                alternativeItemName = inventory.second,
                confidence = 74,
                issueSignal = "Observation severity ${inputs.latestObservationSeverity}",
                agriculturalNeed = "Pest or disease control",
                recommendedAction = "Confirm symptoms, then apply approved guidance",
                productCategory = "Chemicals",
                rationale = "Severity rule: high/critical observation escalates pest-control advice."
            )
            "medium" in normalized -> Recommendation(
                type = RecommendationType.PEST_CONTROL,
                title = "Monitor pest pressure",
                message = composeMessage(
                    issue = "Latest observation severity: ${inputs.latestObservationSeverity}",
                    need = "Close monitoring for spread",
                    action = "Revisit within 48 hours and compare symptom spread",
                    category = "Chemicals / crop protection",
                    why = "Moderate severity warrants monitoring before full treatment.",
                    inventory = inventoryMessage
                ),
                priority = "Medium",
                suggestedItemName = inventory.first,
                alternativeItemName = inventory.second,
                confidence = 62,
                issueSignal = "Observation severity ${inputs.latestObservationSeverity}",
                agriculturalNeed = "Pest pressure monitoring",
                recommendedAction = "Revisit within 48 hours",
                productCategory = "Chemicals",
                rationale = "Severity rule: medium observation → monitor before escalating treatment."
            )
            else -> null
        }
    }

    private fun composeMessage(
        issue: String,
        need: String,
        action: String,
        category: String,
        why: String,
        inventory: String = ""
    ): String = buildString {
        append("Issue: $issue. Need: $need. Action: $action. Category: $category.")
        append(" Why: $why")
        if (inventory.isNotBlank()) append(" $inventory")
        append(" Decision support only — confirm in the field.")
    }

    private fun inventoryHint(
        category: String,
        items: List<InventoryItemEntity>
    ): Pair<String?, String?> {
        val categoryItems = items.filter { item ->
            item.category.equals(category, ignoreCase = true)
        }
        val inStock = categoryItems.filter { it.quantity > 0 }.maxByOrNull { it.quantity }
        val alternative = categoryItems
            .filter { it.quantity > 0 && it.id != inStock?.id }
            .maxByOrNull { it.quantity }
        return inStock?.name to alternative?.name
    }

    private fun inventoryMessage(inventory: Pair<String?, String?>): String {
        val (primary, alternative) = inventory
        return when {
            primary != null && alternative != null ->
                "In-stock options (category hints): $primary; alternative: $alternative."
            primary != null ->
                "In-stock option (category hint): $primary."
            alternative != null ->
                "Preferred category low on stock. Alternative in stock: $alternative."
            else -> "No matching inventory stock found for this category."
        }
    }

    private fun priorityRank(priority: String): Int = when (priority.lowercase(Locale.getDefault())) {
        "critical" -> 4
        "high" -> 3
        "medium" -> 2
        else -> 1
    }

    private data class StageAdvice(
        val need: String,
        val action: String,
        val title: String,
        val priority: String,
        val confidence: Int,
        val rationale: String
    )

    companion object {
        private const val STALE_MINUTES = 15L
    }
}
