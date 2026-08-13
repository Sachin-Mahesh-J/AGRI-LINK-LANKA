package com.example.agriscout.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.agriscout.AgriScoutApplication

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AgriScoutApplication ?: return Result.failure()
        return runCatching {
            val result = app.appContainer.syncRepository.syncPendingData()
            if (result.requiresAuthentication) {
                return@runCatching Result.retry()
            }
            if (result.waitingForConnectivity) {
                return@runCatching Result.retry()
            }
            val outputData = Data.Builder()
                .putInt(KEY_FARMS_SYNCED, result.farmsSynced)
                .putInt(KEY_REPORTS_SYNCED, result.reportsSynced)
                .putInt(KEY_VISITS_SYNCED, result.visitsSynced)
                .putInt(KEY_INVENTORY_REQUESTS_SYNCED, result.inventoryRequestsSynced)
                .putInt(KEY_SENSOR_READINGS_SYNCED, result.sensorReadingsSynced)
                .putInt(KEY_FARMS_RESTORED, result.farmsRestored)
                .putInt(KEY_REPORTS_RESTORED, result.reportsRestored)
                .putInt(KEY_VISITS_RESTORED, result.visitsRestored)
                .putInt(KEY_INVENTORY_REQUESTS_RESTORED, result.inventoryRequestsRestored)
                .putInt(KEY_SENSOR_READINGS_RESTORED, result.sensorReadingsRestored)
                .putInt(KEY_FAILURES, result.failures)
                .putBoolean(KEY_WAITING_FOR_CONNECTIVITY, result.waitingForConnectivity)
                .putStringArray(KEY_ERRORS, result.errors.toTypedArray())
                .putString(KEY_MESSAGE, result.message)
                .build()
            // Per-item failures remain retryable via PENDING/FAILED queues on the next run.
            Result.success(outputData)
        }.fold(
            onSuccess = { it },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val KEY_FARMS_SYNCED = "farms_synced"
        const val KEY_REPORTS_SYNCED = "reports_synced"
        const val KEY_VISITS_SYNCED = "visits_synced"
        const val KEY_INVENTORY_REQUESTS_SYNCED = "inventory_requests_synced"
        const val KEY_SENSOR_READINGS_SYNCED = "sensor_readings_synced"
        const val KEY_FARMS_RESTORED = "farms_restored"
        const val KEY_REPORTS_RESTORED = "reports_restored"
        const val KEY_VISITS_RESTORED = "visits_restored"
        const val KEY_INVENTORY_REQUESTS_RESTORED = "inventory_requests_restored"
        const val KEY_SENSOR_READINGS_RESTORED = "sensor_readings_restored"
        const val KEY_FAILURES = "failures"
        const val KEY_WAITING_FOR_CONNECTIVITY = "waiting_for_connectivity"
        const val KEY_ERRORS = "errors"
        const val KEY_MESSAGE = "message"
    }
}
