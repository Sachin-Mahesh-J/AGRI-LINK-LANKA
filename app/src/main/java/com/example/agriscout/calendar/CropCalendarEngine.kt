package com.example.agriscout.calendar

import com.example.agriscout.ai.HarvestPhase
import com.example.agriscout.ai.HarvestYieldEstimator
import com.example.agriscout.ai.toOfficerMessage
import com.example.agriscout.crop.CropStage
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.InventoryItemEntity

class CropCalendarEngine(
    private val profileProvider: CropProfileProvider,
    private val harvestYieldEstimator: HarvestYieldEstimator = HarvestYieldEstimator()
) {
    fun profileFor(farm: FarmEntity): CropCalendarProfile = profileProvider.loadProfile(farm.cropType)

    fun dueActivities(
        farm: FarmEntity,
        ageDays: Long,
        currentStage: CropStage,
        inventoryItems: List<InventoryItemEntity>,
        context: ConditionContext,
        upcomingWindowDays: Int = 7
    ): List<ScheduledActivity> {
        val profile = profileFor(farm)
        val stageName = currentStage.name
        val scheduled = mutableListOf<ScheduledActivity>()

        profile.stages.forEach { stageSchedule ->
            stageSchedule.activities.forEach { template ->
                val inStage = stageSchedule.stage == stageName
                val inDayRange = ageDays.toInt() in stageSchedule.dayRange
                if (!inStage && !inDayRange) return@forEach

                val status = when {
                    ageDays.toInt() in stageSchedule.dayRange && inStage -> ActivityStatus.DUE
                    ageDays.toInt() < stageSchedule.dayRange.first &&
                        stageSchedule.dayRange.first - ageDays.toInt() <= upcomingWindowDays -> ActivityStatus.UPCOMING
                    else -> return@forEach
                }

                scheduled += buildScheduledActivity(
                    farm = farm,
                    template = template,
                    stage = stageSchedule.stage,
                    ageDays = ageDays,
                    status = status,
                    inventoryItems = inventoryItems,
                    context = context
                )
            }
        }

        harvestOutlook(profile, farm, ageDays, context)?.let { scheduled += it }

        return scheduled.distinctBy { it.activityId }
    }

    fun triggeredTreatments(
        farm: FarmEntity,
        ageDays: Long,
        detectedIssueId: String?,
        inventoryItems: List<InventoryItemEntity>,
        context: ConditionContext
    ): List<ScheduledActivity> {
        if (detectedIssueId.isNullOrBlank()) return emptyList()
        val profile = profileFor(farm)
        return profile.diseaseWatchWindows
            .filter { window ->
                window.issueId.equals(detectedIssueId, ignoreCase = true) &&
                    ageDays.toInt() in window.dayRange
            }
            .map { window ->
                val trigger = window.triggerActivity
                val template = CalendarActivityTemplate(
                    id = trigger.id,
                    type = trigger.type,
                    title = trigger.title,
                    productCategory = trigger.productCategory,
                    preferredProducts = trigger.preferredProducts,
                    dosePerAcreKg = 0.0,
                    unit = "",
                    notes = trigger.doseNote
                )
                buildScheduledActivity(
                    farm = farm,
                    template = template,
                    stage = window.stage,
                    ageDays = ageDays,
                    status = ActivityStatus.TRIGGERED,
                    inventoryItems = inventoryItems,
                    context = context,
                    source = "calendar_trigger",
                    doseNote = trigger.doseNote,
                    issueSignal = "Detected issue ${window.issueId}",
                    agriculturalNeed = "Crop protection for ${window.issueId}",
                    recommendedAction = trigger.title,
                    rationale = "Disease watch window matched issue ${window.issueId} on day $ageDays.",
                    confidence = 78
                )
            }
    }

    private fun harvestOutlook(
        profile: CropCalendarProfile,
        farm: FarmEntity,
        ageDays: Long,
        context: ConditionContext
    ): ScheduledActivity? {
        val estimate = harvestYieldEstimator.estimate(
            profile = profile,
            farm = farm,
            ageDays = ageDays,
            latestObservationSeverity = context.latestObservationSeverity,
            sensorReading = context.sensorReading
        ) ?: return null

        // Show outlook once crop is within 30 days of the window, during it, or shortly after.
        val earlyPreviewDay = (estimate.windowStartDay - 30).coerceAtLeast(0)
        if (ageDays.toInt() < earlyPreviewDay && estimate.phase == HarvestPhase.BEFORE_WINDOW) {
            return null
        }

        val status = when (estimate.phase) {
            HarvestPhase.IN_WINDOW -> ActivityStatus.DUE
            HarvestPhase.BEFORE_WINDOW -> ActivityStatus.UPCOMING
            HarvestPhase.AFTER_WINDOW -> ActivityStatus.DUE
            HarvestPhase.UNKNOWN -> ActivityStatus.UPCOMING
        }

        val message = buildString {
            append(estimate.toOfficerMessage())
            append(" Why: ${estimate.rationale}")
        }

        return ScheduledActivity(
            activityId = "${profile.cropId}-harvest-forecast",
            type = "HARVEST",
            title = when (estimate.phase) {
                HarvestPhase.BEFORE_WINDOW -> "Estimated harvest window"
                HarvestPhase.IN_WINDOW -> "Harvest and yield outlook"
                HarvestPhase.AFTER_WINDOW -> "Harvest window review"
                HarvestPhase.UNKNOWN -> "Harvest timing estimate"
            },
            stage = CropStage.HARVEST_READY.name,
            dayOfSeason = ageDays,
            status = status,
            message = message,
            productCategory = "",
            preferredProducts = emptyList(),
            suggestedQuantity = estimate.estimatedYieldMaxTonnes,
            quantityUnit = "tonnes",
            suggestedItemName = null,
            alternativeItemName = null,
            priority = if (estimate.phase == HarvestPhase.IN_WINDOW) "High" else "Medium",
            source = "heuristic",
            confidence = estimate.confidence,
            issueSignal = "Crop age day ${estimate.ageDays}",
            agriculturalNeed = "Harvest planning and yield estimation",
            recommendedAction = "Confirm maturity on site; plan labor and logistics",
            rationale = estimate.rationale
        )
    }

    private fun buildScheduledActivity(
        farm: FarmEntity,
        template: CalendarActivityTemplate,
        stage: String,
        ageDays: Long,
        status: ActivityStatus,
        inventoryItems: List<InventoryItemEntity>,
        context: ConditionContext,
        source: String = "calendar",
        doseNote: String? = null,
        issueSignal: String? = null,
        agriculturalNeed: String? = null,
        recommendedAction: String? = null,
        rationale: String? = null,
        confidence: Int? = null
    ): ScheduledActivity {
        val (adjustedStatus, conditionMessage) = ConditionAdjuster.adjust(template, status, context)
        val match = ProductMatcher.match(template.productCategory, template.preferredProducts, inventoryItems)
        val totalQuantity = DoseCalculator.totalQuantity(template.dosePerAcreKg, farm.landSize)
        val quantityText = DoseCalculator.formatQuantity(totalQuantity, template.unit)

        val need = agriculturalNeed ?: when (template.type) {
            "FERTILIZER" -> "Stage-appropriate nutrition"
            "IRRIGATION" -> "Crop water management"
            "CHEMICAL", "PEST_CONTROL" -> "Crop protection"
            "HARVEST" -> "Harvest readiness"
            else -> "Scheduled agronomic activity"
        }
        val action = recommendedAction ?: template.title
        val why = rationale ?: buildString {
            append("Crop calendar activity ${template.id} for day ${ageDays.toInt()}.")
            if (conditionMessage.isNotBlank()) append(" $conditionMessage")
        }

        val message = buildString {
            append("Issue: Day ${ageDays.toInt()} · ${stage.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}. ")
            append("Need: $need. Action: $action. ")
            if (template.productCategory.isNotBlank()) {
                append("Category: ${template.productCategory}")
                if (template.preferredProducts.isNotEmpty()) {
                    append(" (preferred options: ${template.preferredProducts.joinToString(", ")})")
                }
                append(". ")
            }
            append("Why: $why")
            if (quantityText != null) append(" Recommended total: $quantityText.")
            doseNote?.takeIf { it.isNotBlank() }?.let { append(" $it") }
            match.primary?.let { append(" In-stock hint: ${it.name}.") }
            match.alternative?.let { append(" Alternative: ${it.name}.") }
            append(" Decision support only.")
        }

        val activityConfidence = confidence ?: when (adjustedStatus) {
            ActivityStatus.TRIGGERED -> 80
            ActivityStatus.DUE -> 75
            ActivityStatus.BLOCKED -> 70
            ActivityStatus.UPCOMING -> 60
        }

        return ScheduledActivity(
            activityId = template.id,
            type = template.type,
            title = template.title,
            stage = stage,
            dayOfSeason = ageDays,
            status = adjustedStatus,
            message = message.trim(),
            productCategory = template.productCategory,
            preferredProducts = template.preferredProducts,
            suggestedQuantity = totalQuantity,
            quantityUnit = template.unit.takeIf { it.isNotBlank() },
            suggestedItemName = match.primary?.name,
            alternativeItemName = match.alternative?.name,
            priority = priorityFor(adjustedStatus, template.type),
            source = source,
            doseNote = doseNote,
            confidence = activityConfidence,
            issueSignal = issueSignal ?: "Calendar day ${ageDays.toInt()}",
            agriculturalNeed = need,
            recommendedAction = action,
            rationale = why
        )
    }

    private fun priorityFor(status: ActivityStatus, type: String): String = when {
        status == ActivityStatus.TRIGGERED -> "Critical"
        status == ActivityStatus.BLOCKED -> "High"
        status == ActivityStatus.DUE && type in setOf("CHEMICAL", "IRRIGATION") -> "High"
        status == ActivityStatus.DUE -> "Medium"
        else -> "Low"
    }
}
