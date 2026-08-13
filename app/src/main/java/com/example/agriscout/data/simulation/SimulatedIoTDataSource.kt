package com.example.agriscout.data.simulation

import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.SensorReadingEntity
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

object SensorStatus {
    const val NORMAL = "Normal"
    const val WARNING = "Warning"
    const val CRITICAL = "Critical"
}

class SimulatedIoTDataSource : IoTDataSource {
    override fun generateReading(farm: FarmEntity, now: Long): SensorReadingEntity {
        val seed = abs(farm.id.hashCode() % 1000) / 1000.0
        val phase = (now / 30_000.0) + seed * PI
        val cropAdjustment = cropMoistureAdjustment(farm.cropType)
        val soilMoisture = (48 + cropAdjustment + sin(phase) * 18).coerceIn(8.0, 96.0)
        val temperature = (29 + sin(phase / 1.7 + seed) * 7).coerceIn(16.0, 44.0)
        val humidity = (62 + sin(phase / 1.3 + 1.4) * 22).coerceIn(25.0, 96.0)
        val light = (42_000 + sin(phase / 2.1) * 26_000).coerceIn(1_500.0, 90_000.0)
        val waterLevel = (55 + sin(phase / 1.5 + 2.2) * 28).coerceIn(4.0, 100.0)

        return SensorReadingEntity(
            id = UUID.randomUUID().toString(),
            farmId = farm.id,
            userId = farm.userId,
            deviceId = farm.assignedDeviceId,
            soilMoisturePercent = soilMoisture,
            temperatureCelsius = temperature,
            humidityPercent = humidity,
            lightIntensityLux = light,
            waterLevelPercent = waterLevel,
            status = classify(soilMoisture, temperature, humidity, waterLevel),
            source = SensorReadingSource.SIMULATED,
            recordedAt = now,
            updatedAt = now
        )
    }

    fun classify(
        soilMoisturePercent: Double,
        temperatureCelsius: Double,
        humidityPercent: Double,
        waterLevelPercent: Double
    ): String = when {
        soilMoisturePercent < 18 || temperatureCelsius > 40 || waterLevelPercent < 12 -> SensorStatus.CRITICAL
        soilMoisturePercent < 32 || temperatureCelsius > 35 || humidityPercent < 35 || waterLevelPercent < 25 -> SensorStatus.WARNING
        else -> SensorStatus.NORMAL
    }

    private fun cropMoistureAdjustment(cropType: String): Double {
        val normalized = cropType.lowercase(Locale.getDefault())
        return when {
            "rice" in normalized || "paddy" in normalized -> 12.0
            "cotton" in normalized -> -5.0
            "wheat" in normalized -> -2.0
            else -> 0.0
        }
    }
}
