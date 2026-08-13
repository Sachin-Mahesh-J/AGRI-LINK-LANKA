package com.example.agriscout.calendar

import java.util.Locale
import kotlin.math.max

object DoseCalculator {
    fun parseAcres(landSize: String): Double {
        val normalized = landSize.trim().lowercase(Locale.getDefault())
        val number = Regex("""([\d.]+)""").find(normalized)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
        return when {
            "hectare" in normalized || "ha" in normalized -> number * 2.471
            "acre" in normalized -> number
            else -> max(number, 1.0)
        }
    }

    fun totalQuantity(dosePerAcreKg: Double, landSize: String): Double? {
        if (dosePerAcreKg <= 0.0) return null
        val acres = parseAcres(landSize)
        return (dosePerAcreKg * acres * 10).toInt() / 10.0
    }

    fun formatQuantity(quantity: Double?, unit: String): String? {
        if (quantity == null || quantity <= 0.0) return null
        val normalizedUnit = unit.ifBlank { "units" }
        return "${quantity.toInt()} $normalizedUnit"
    }
}
