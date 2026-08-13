package com.example.agriscout.calendar

data class CropCalendarProfile(
    val cropId: String,
    val displayName: String,
    val stages: List<CropStageSchedule>,
    val diseaseWatchWindows: List<DiseaseWatchWindow>,
    val harvestWindow: HarvestWindow
)

data class CropStageSchedule(
    val stage: String,
    val dayRange: IntRange,
    val activities: List<CalendarActivityTemplate>
)

data class CalendarActivityTemplate(
    val id: String,
    val type: String,
    val title: String,
    val productCategory: String,
    val preferredProducts: List<String>,
    val dosePerAcreKg: Double,
    val unit: String,
    val minSoilMoisture: Double? = null,
    val maxTemperatureCelsius: Double? = null,
    val notes: String = ""
)

data class DiseaseWatchWindow(
    val issueId: String,
    val stage: String,
    val dayRange: IntRange,
    val triggerActivity: TriggerActivityTemplate
)

data class TriggerActivityTemplate(
    val id: String,
    val type: String,
    val title: String,
    val productCategory: String,
    val preferredProducts: List<String>,
    val doseNote: String
)

data class HarvestWindow(
    val dayRange: IntRange,
    val yieldPerAcreTonnesBaseline: Double
)

enum class ActivityStatus {
    DUE,
    UPCOMING,
    BLOCKED,
    TRIGGERED
}

data class ScheduledActivity(
    val activityId: String,
    val type: String,
    val title: String,
    val stage: String,
    val dayOfSeason: Long,
    val status: ActivityStatus,
    val message: String,
    val productCategory: String,
    val preferredProducts: List<String>,
    val suggestedQuantity: Double?,
    val quantityUnit: String?,
    val suggestedItemName: String?,
    val alternativeItemName: String?,
    val priority: String,
    val source: String = "calendar",
    val doseNote: String? = null,
    val confidence: Int? = null,
    val issueSignal: String? = null,
    val agriculturalNeed: String? = null,
    val recommendedAction: String? = null,
    val rationale: String? = null
)
