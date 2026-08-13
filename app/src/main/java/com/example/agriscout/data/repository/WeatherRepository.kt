package com.example.agriscout.data.repository

import com.example.agriscout.data.local.WeatherSnapshotDao
import com.example.agriscout.data.local.WeatherSnapshotEntity
import com.example.agriscout.data.local.WeatherWarningDao
import com.example.agriscout.data.remote.WeatherRemoteService
import kotlinx.coroutines.flow.Flow

class WeatherRepository(
    private val snapshotDao: WeatherSnapshotDao,
    private val warningDao: WeatherWarningDao,
    private val remoteService: WeatherRemoteService
) {
    fun observeLatestSnapshot(): Flow<WeatherSnapshotEntity?> = snapshotDao.observeLatestSnapshot()

    suspend fun refresh(latitude: Double, longitude: Double, locationLabel: String): WeatherSnapshotEntity {
        val payload = remoteService.fetchCurrentWeather(latitude, longitude, locationLabel)
        snapshotDao.upsert(payload.snapshot)
        if (payload.warnings.isNotEmpty()) {
            warningDao.upsertAll(payload.warnings)
        }
        return payload.snapshot
    }
}
