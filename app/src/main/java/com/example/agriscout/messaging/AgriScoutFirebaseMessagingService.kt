package com.example.agriscout.messaging

import com.example.agriscout.AgriScoutApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AgriScoutFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val repository = (application as? AgriScoutApplication)?.appContainer?.notificationRepository ?: return
        serviceScope.launch {
            runCatching { repository.onNewToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val app = application as? AgriScoutApplication ?: return
        val repository = app.appContainer.notificationRepository
        val dispatcher = NotificationDispatcher(applicationContext)
        serviceScope.launch {
            val warning = repository.saveIncomingAlert(message.data)
            dispatcher.showAlert(warning)
        }
    }
}
