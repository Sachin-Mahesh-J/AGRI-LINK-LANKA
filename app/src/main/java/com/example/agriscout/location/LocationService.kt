package com.example.agriscout.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class FieldLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val capturedAt: Long = System.currentTimeMillis(),
    val source: String = GpsCaptureSource.DEVICE_GPS
)

object GpsCaptureSource {
    const val DEVICE_GPS = "device_gps"
    const val LAST_KNOWN = "last_known"
}

class LocationService(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): FieldLocation? {
        if (!hasLocationPermission()) return null
        val currentLocation = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()
        }
        if (currentLocation != null) {
            return FieldLocation(
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                accuracyMeters = currentLocation.accuracy.takeIf { it > 0f },
                capturedAt = System.currentTimeMillis(),
                source = GpsCaptureSource.DEVICE_GPS
            )
        }
        val lastKnown = runCatching { fusedLocationClient.lastLocation.await() }.getOrNull() ?: return null
        return FieldLocation(
            latitude = lastKnown.latitude,
            longitude = lastKnown.longitude,
            accuracyMeters = lastKnown.accuracy.takeIf { it > 0f },
            capturedAt = lastKnown.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            source = GpsCaptureSource.LAST_KNOWN
        )
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 10_000L
    }
}
