package com.example.agriscout

import com.example.agriscout.data.remote.WeatherConfigurationException
import com.example.agriscout.data.remote.WeatherRemoteService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRemoteServiceTest {
    @Test
    fun missingApiKeyFailsBeforeNetworkCall() = runBlocking {
        val result = runCatching {
            WeatherRemoteService("").fetchCurrentWeather(6.9, 79.8, "Test")
        }

        assertTrue(result.exceptionOrNull() is WeatherConfigurationException)
    }
}
