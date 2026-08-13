package com.example.agriscout.data.repository

import com.example.agriscout.auth.FirebaseAuthService
import com.example.agriscout.data.local.FcmTokenDao
import com.example.agriscout.data.local.FcmTokenEntity
import com.example.agriscout.data.local.WeatherWarningDao
import com.example.agriscout.data.local.WeatherWarningEntity
import com.example.agriscout.data.remote.FcmRemoteService
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.util.UUID

class NotificationRepository(
    private val authService: FirebaseAuthService,
    private val tokenDao: FcmTokenDao,
    private val warningDao: WeatherWarningDao,
    private val remoteService: FcmRemoteService,
    private val firebaseMessaging: FirebaseMessaging?
) {
    suspend fun registerCurrentDevice(): String {
        val userId = authService.currentUserId ?: error("Login required before enabling push alerts.")
        val messaging = firebaseMessaging ?: error("Firebase Messaging is not configured.")
        val token = messaging.token.await()
        saveAndUploadToken(userId, token)
        return token
    }

    suspend fun onNewToken(token: String) {
        val userId = authService.currentUserId ?: return
        saveAndUploadToken(userId, token)
    }

    suspend fun saveIncomingAlert(data: Map<String, String>): WeatherWarningEntity {
        val now = System.currentTimeMillis()
        val warning = WeatherWarningEntity(
            id = data["id"].takeUnless { it.isNullOrBlank() } ?: "fcm-${UUID.randomUUID()}",
            title = data["title"].takeUnless { it.isNullOrBlank() } ?: "AgriScout Alert",
            message = data["message"].takeUnless { it.isNullOrBlank() } ?: "New field scouting alert received.",
            affectedArea = data["affectedArea"].takeUnless { it.isNullOrBlank() } ?: "Your farms",
            severity = data["severity"].takeUnless { it.isNullOrBlank() } ?: "Medium",
            validUntil = data["validUntil"]?.toLongOrNull() ?: now + 24 * 60 * 60 * 1000,
            updatedAt = now,
            source = "FCM",
            actionRoute = data["actionRoute"]
        )
        warningDao.upsertAll(listOf(warning))
        return warning
    }

    private suspend fun saveAndUploadToken(userId: String, token: String) {
        val now = System.currentTimeMillis()
        tokenDao.upsert(FcmTokenEntity(token = token, userId = userId, updatedAt = now, synced = false))
        remoteService.uploadDeviceToken(userId, token, now)
        tokenDao.markSynced(token)
    }
}
