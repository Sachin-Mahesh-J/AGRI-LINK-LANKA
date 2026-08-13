package com.example.agriscout.crop

import java.util.Locale
import java.util.concurrent.TimeUnit

enum class CropStage(val label: String) {
    GERMINATION("Germination"),
    VEGETATIVE("Vegetative"),
    FLOWERING("Flowering"),
    HARVEST_READY("Harvest-ready"),
    UNKNOWN("Unknown")
}

data class CropLifecycleEstimate(
    val stage: CropStage,
    val ageDays: Long?,
    val summary: String
)

class CropLifecycleEstimator {
    fun estimate(cropType: String, plantingDate: Long?, now: Long = System.currentTimeMillis()): CropLifecycleEstimate {
        if (plantingDate == null || plantingDate <= 0L || plantingDate > now) {
            return CropLifecycleEstimate(
                stage = CropStage.UNKNOWN,
                ageDays = null,
                summary = "Add a planting date to estimate crop stage."
            )
        }

        val ageDays = TimeUnit.MILLISECONDS.toDays(now - plantingDate).coerceAtLeast(0)
        val profile = LifecycleProfile.forCrop(cropType)
        val stage = when {
            ageDays < profile.germinationDays -> CropStage.GERMINATION
            ageDays < profile.vegetativeDays -> CropStage.VEGETATIVE
            ageDays < profile.floweringDays -> CropStage.FLOWERING
            else -> CropStage.HARVEST_READY
        }
        return CropLifecycleEstimate(
            stage = stage,
            ageDays = ageDays,
            summary = "${stage.label} stage estimated from $ageDays days since planting."
        )
    }
}

private data class LifecycleProfile(
    val germinationDays: Int,
    val vegetativeDays: Int,
    val floweringDays: Int
) {
    companion object {
        fun forCrop(cropType: String): LifecycleProfile {
            val normalized = cropType.lowercase(Locale.getDefault())
            return when {
                "rice" in normalized || "paddy" in normalized -> LifecycleProfile(15, 60, 100)
                "wheat" in normalized -> LifecycleProfile(10, 55, 100)
                "maize" in normalized || "corn" in normalized -> LifecycleProfile(10, 45, 85)
                "cotton" in normalized -> LifecycleProfile(12, 55, 115)
                "tomato" in normalized -> LifecycleProfile(10, 40, 80)
                else -> LifecycleProfile(14, 50, 95)
            }
        }
    }
}
