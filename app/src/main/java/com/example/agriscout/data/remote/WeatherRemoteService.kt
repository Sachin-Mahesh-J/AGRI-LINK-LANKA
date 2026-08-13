package com.example.agriscout.data.remote

import com.example.agriscout.data.local.WeatherSnapshotEntity
import com.example.agriscout.data.local.WeatherWarningEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

class WeatherRemoteService(private val apiKey: String) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    suspend fun fetchCurrentWeather(latitude: Double, longitude: Double, locationLabel: String): WeatherPayload =
        withContext(Dispatchers.IO) {
            if (!isConfigured) {
                throw WeatherConfigurationException()
            }
            val url = URL(
                "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$apiKey&units=metric"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            try {
                val body = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw WeatherApiException(connection.responseCode, error.toWeatherErrorMessage(connection.responseCode))
                }
                body.toWeatherPayload(latitude, longitude, locationLabel)
            } finally {
                connection.disconnect()
            }
        }
}

class WeatherConfigurationException : IllegalStateException(
    "Weather API key is missing. Add WEATHER_API_KEY to local.properties."
)

class WeatherApiException(code: Int, message: String) : IllegalStateException(
    "Weather API failed ($code): $message"
)

data class WeatherPayload(
    val snapshot: WeatherSnapshotEntity,
    val warnings: List<WeatherWarningEntity>
)

private fun String.toWeatherPayload(latitude: Double, longitude: Double, locationLabel: String): WeatherPayload {
    val json = JSONObject(this)
    val main = json.getJSONObject("main")
    val wind = json.optJSONObject("wind")
    val weather = json.optJSONArray("weather")?.optJSONObject(0)
    val condition = weather?.optString("description").orEmpty().replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
    val temperature = main.getDouble("temp")
    val humidity = main.getInt("humidity")
    val windSpeed = wind?.optDouble("speed") ?: 0.0
    val now = System.currentTimeMillis()
    val riskSummary = buildRiskSummary(temperature, humidity, windSpeed, condition)
    val snapshot = WeatherSnapshotEntity(
        id = "${latitude.formatCoord()},${longitude.formatCoord()}",
        locationLabel = json.optString("name").ifBlank { locationLabel },
        latitude = latitude,
        longitude = longitude,
        temperatureCelsius = temperature,
        humidityPercent = humidity,
        windSpeedMetersPerSecond = windSpeed,
        condition = condition.ifBlank { "Current conditions unavailable" },
        forecastSummary = "Current: ${temperature.toInt()} C, $humidity% humidity, ${windSpeed.formatOne()} m/s wind.",
        riskSummary = riskSummary,
        fetchedAt = now
    )
    val warnings = buildWeatherWarnings(snapshot, now)
    return WeatherPayload(snapshot, warnings)
}

private fun buildRiskSummary(temperature: Double, humidity: Int, windSpeed: Double, condition: String): String {
    val risks = mutableListOf<String>()
    if (humidity >= 85) risks += "High humidity can increase fungal disease pressure."
    if (temperature >= 34) risks += "Heat stress risk is elevated; check irrigation and wilting."
    if (windSpeed >= 8) risks += "Strong wind can reduce spray accuracy and increase crop stress."
    if (condition.contains("rain", ignoreCase = true)) risks += "Rainy conditions favor disease spread and may delay field operations."
    return risks.ifEmpty { listOf("No major weather risk detected from current conditions.") }.joinToString(" ")
}

private fun buildWeatherWarnings(snapshot: WeatherSnapshotEntity, now: Long): List<WeatherWarningEntity> {
    val warnings = mutableListOf<WeatherWarningEntity>()
    if (snapshot.humidityPercent >= 85) {
        warnings += snapshot.toWarning(
            title = "High fungal disease pressure",
            message = "Humidity is ${snapshot.humidityPercent}%. Scout susceptible crops for leaf spots, blight, and mildew symptoms.",
            severity = "High",
            now = now
        )
    }
    if (snapshot.temperatureCelsius >= 34) {
        warnings += snapshot.toWarning(
            title = "Heat stress risk",
            message = "Temperature is ${snapshot.temperatureCelsius.toInt()} C. Prioritize irrigation checks and inspect young crops for wilting.",
            severity = "Medium",
            now = now
        )
    }
    return warnings
}

private fun WeatherSnapshotEntity.toWarning(title: String, message: String, severity: String, now: Long): WeatherWarningEntity =
    WeatherWarningEntity(
        id = "api-${UUID.nameUUIDFromBytes("$title-$id".toByteArray())}",
        title = title,
        message = message,
        affectedArea = locationLabel,
        severity = severity,
        validUntil = now + 6 * 60 * 60 * 1000,
        updatedAt = now,
        source = "WEATHER_API"
    )

private fun Double.formatCoord(): String = String.format(Locale.US, "%.4f", this)
private fun Double.formatOne(): String = String.format(Locale.US, "%.1f", this)

private fun String.toWeatherErrorMessage(code: Int): String {
    val apiMessage = runCatching { JSONObject(this).optString("message") }.getOrNull()
        ?.takeIf { it.isNotBlank() }
    return apiMessage ?: when (code) {
        401 -> "Invalid or inactive OpenWeather API key. New keys can take time to activate."
        429 -> "Weather API rate limit reached. Try again later."
        else -> "Unable to fetch weather. Check connectivity and provider configuration."
    }
}
