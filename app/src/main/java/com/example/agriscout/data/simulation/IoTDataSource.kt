package com.example.agriscout.data.simulation

import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.SensorReadingEntity

/** Provenance for sensor readings — kept compatible with Firestore `source`. */
object SensorReadingSource {
    const val SIMULATED = "simulated"
    const val DEVICE = "device"
}

/**
 * Pluggable IoT reading producer. Simulation is the default implementation;
 * real device readings arrive via sync from the secure ingest path.
 */
interface IoTDataSource {
    fun generateReading(farm: FarmEntity, now: Long = System.currentTimeMillis()): SensorReadingEntity
}
