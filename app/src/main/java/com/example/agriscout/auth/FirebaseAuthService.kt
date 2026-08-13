package com.example.agriscout.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class OfficerSession(
    val userId: String,
    val email: String?
)

data class OfficerAccessRecord(
    val role: String,
    val status: String
)

object OfficerAccessStatus {
    const val PENDING = "pending"
    const val ACTIVE = "active"
    const val INACTIVE = "inactive"
}

object OfficerRoles {
    const val FIELD_OFFICER = "field_officer"
    const val ADMIN = "admin"
    const val SUPER_ADMIN = "super_admin"
    const val SUPPLIER = "supplier"
    const val BUYER = "buyer"
}

class FirebaseAuthService(
    private val firebaseAuth: FirebaseAuth?,
    private val firestore: FirebaseFirestore? = null
) {
    val isConfigured: Boolean
        get() = firebaseAuth != null

    val currentUserId: String?
        get() = firebaseAuth?.currentUser?.uid

    val currentSession: OfficerSession?
        get() = firebaseAuth?.currentUser?.let { user ->
            OfficerSession(
                userId = user.uid,
                email = user.email
            )
        }

    fun isLoggedIn(): Boolean = firebaseAuth?.currentUser != null

    suspend fun login(email: String, password: String) {
        val auth = firebaseAuth ?: throw FirebaseConfigurationException()
        try {
            val user = auth.signInWithEmailAndPassword(email, password).await().user
            user?.let { ensureOfficerDirectoryEntry(it) }
        } catch (exception: FirebaseAuthException) {
            throw AuthException(exception.toOfficerMessage(), exception)
        }
    }

    suspend fun register(email: String, password: String) {
        val auth = firebaseAuth ?: throw FirebaseConfigurationException()
        try {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user
            user?.let { ensureOfficerDirectoryEntry(it) }
        } catch (exception: FirebaseAuthException) {
            throw AuthException(exception.toOfficerMessage(), exception)
        }
    }

    fun logout() {
        firebaseAuth?.signOut()
    }

    /**
     * Reads the officer access record used by Firestore RBAC.
     * Returns null only when Firebase Auth/Firestore is not configured.
     */
    suspend fun fetchOfficerAccess(): OfficerAccessRecord? {
        val userId = currentUserId ?: return null
        val db = firestore ?: return null
        val snapshot = db.collection("userAccess").document(userId).get().await()
        if (!snapshot.exists()) {
            return OfficerAccessRecord(
                role = OfficerRoles.FIELD_OFFICER,
                status = OfficerAccessStatus.PENDING
            )
        }
        return OfficerAccessRecord(
            role = snapshot.getString("role")?.takeIf { it.isNotBlank() } ?: OfficerRoles.FIELD_OFFICER,
            status = snapshot.getString("status")?.takeIf { it.isNotBlank() } ?: OfficerAccessStatus.PENDING
        )
    }

    private suspend fun ensureOfficerDirectoryEntry(user: FirebaseUser) {
        val db = firestore ?: return
        val accessRef = db.collection("userAccess").document(user.uid)
        val profileRef = db.collection("staffProfiles").document(user.uid)
        val now = System.currentTimeMillis()
        if (!accessRef.get().await().exists()) {
            accessRef.set(
                mapOf(
                    "role" to OfficerRoles.FIELD_OFFICER,
                    "status" to OfficerAccessStatus.PENDING,
                    "createdAt" to now,
                    "updatedAt" to now
                )
            ).await()
        }
        if (!profileRef.get().await().exists()) {
            profileRef.set(
                mapOf(
                    "displayName" to user.email?.substringBefore("@").orEmpty(),
                    "email" to user.email.orEmpty(),
                    "phone" to "",
                    "createdAt" to now,
                    "updatedAt" to now
                )
            ).await()
        }
    }
}

class FirebaseConfigurationException : IllegalStateException(
    "Firebase is not configured. Add a valid app/google-services.json and enable Email/Password sign-in in Firebase Console."
)

class AuthException(message: String, cause: Throwable) : IllegalStateException(message, cause)

private fun FirebaseAuthException.toOfficerMessage(): String = when (errorCode) {
    "ERROR_INVALID_EMAIL" -> "Enter a valid officer email address."
    "ERROR_INVALID_CREDENTIAL",
    "ERROR_WRONG_PASSWORD",
    "ERROR_USER_NOT_FOUND" -> "Email or password is incorrect."
    "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already exists for this email."
    "ERROR_WEAK_PASSWORD" -> "Use a stronger password with at least 6 characters."
    "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check your connection and try again."
    else -> localizedMessage ?: "Firebase authentication failed. Please try again."
}
