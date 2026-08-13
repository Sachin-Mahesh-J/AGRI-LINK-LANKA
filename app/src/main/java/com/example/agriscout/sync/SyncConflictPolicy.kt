package com.example.agriscout.sync

import com.example.agriscout.data.local.SyncStatus
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Deterministic offline-first conflict policy for Phase 2 sync.
 *
 * Priority order:
 * 1. Local PENDING / FAILED changes are never overwritten by remote (protect unsynced edits).
 * 2. Local SYNCED records may be refreshed from remote only when remote.updatedAt is strictly newer.
 * 3. Missing local records are inserted from remote as SYNCED.
 * 4. Uploads use document id = remoteId ?: localId with Firestore merge, so retries do not create duplicates.
 *
 * This is intentionally minimal: no CRDT / user-facing conflict UI. Silent overwrites of
 * unsynced local work are rejected.
 */
object SyncConflictPolicy {

    const val SUMMARY: String =
        "Local pending/failed edits win until uploaded; synced records accept newer remote updatedAt; " +
            "uploads merge by remoteId/local id to avoid duplicates."

    /**
     * Whether a remote record should replace or create the local row.
     * Callers must pass null [localSyncStatus] when no local row exists.
     */
    fun shouldApplyRemote(
        localSyncStatus: String?,
        localUpdatedAt: Long,
        remoteUpdatedAt: Long
    ): Boolean {
        if (localSyncStatus == null) return true
        if (localSyncStatus != SyncStatus.SYNCED) return false
        return remoteUpdatedAt > localUpdatedAt
    }

    fun isConnectivityFailure(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            when (current) {
                is UnknownHostException,
                is ConnectException,
                is SocketTimeoutException,
                is InterruptedIOException -> return true
            }
            val className = current.javaClass.name
            if (
                className.contains("FirebaseNetworkException", ignoreCase = true) ||
                className.contains("UnknownHost", ignoreCase = true)
            ) {
                return true
            }
            val message = current.message?.lowercase().orEmpty()
            if (
                "unable to resolve host" in message ||
                "failed to connect" in message ||
                "network is unreachable" in message ||
                "timeout" in message ||
                ("network" in message && ("unavailable" in message || "offline" in message))
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
