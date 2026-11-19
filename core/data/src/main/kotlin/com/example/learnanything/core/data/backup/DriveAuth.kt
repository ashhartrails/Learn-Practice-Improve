package com.example.learnanything.core.data.backup

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Gives a Drive access token, or null when the learner must grant access on screen first. */
interface DriveAuth {
    suspend fun accessToken(): String?
}

/**
 * Uses Google Identity Services' AuthorizationClient (not the deprecated GoogleSignIn classes)
 * for the `drive.appdata` scope. Once the learner has granted access in Settings, later calls
 * (including from background work) return a fresh token without any UI.
 */
@Singleton
class GoogleDriveAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: BackupPrefs,
) : DriveAuth {
    override suspend fun accessToken(): String? {
        val result = runCatching { authorize(context, prefs.accountEmail()) }.getOrNull() ?: return null
        return if (result.hasResolution()) null else result.accessToken
    }

    companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

        fun request(email: String?): AuthorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA)))
            .apply { if (!email.isNullOrBlank()) setAccount(Account(email, "com.google")) }
            .build()

        suspend fun authorize(context: Context, email: String?): AuthorizationResult = suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(context).authorize(request(email))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWith(Result.failure(it)) }
        }
    }
}
