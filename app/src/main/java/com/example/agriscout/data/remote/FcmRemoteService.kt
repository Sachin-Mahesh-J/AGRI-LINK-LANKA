package com.example.agriscout.data.remote

import com.example.agriscout.auth.FirebaseConfigurationException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FcmRemoteService(private val firestore: FirebaseFirestore?) {
    suspend fun uploadDeviceToken(userId: String, token: String, updatedAt: Long) {
        val db = firestore ?: throw FirebaseConfigurationException()
        db.collection("users")
            .document(userId)
            .collection("devices")
            .document(token)
            .set(
                mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to updatedAt,
                    "enabled" to true
                ),
                SetOptions.merge()
            )
            .await()
    }
}
