package com.example.learnanything.feature.settings

import android.app.Activity
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.learnanything.core.data.backup.BackupRepository
import com.example.learnanything.core.data.backup.GoogleDriveAuth
import com.example.learnanything.core.data.backup.Snapshot
import com.example.learnanything.core.model.backup.RestoreMode
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Google Drive backup. Sign-in uses Credential Manager (when a web client id is configured),
 * then Google's AuthorizationClient asks for the drive.appdata scope only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupCard(viewModel: BackupViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingEmail by remember { mutableStateOf<String?>(null) }
    var restoreSheet by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf<(() -> Unit)?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val granted = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }.getOrNull()
            if (granted?.accessToken != null) viewModel.onAccessGranted(pendingEmail)
            else viewModel.onSignInFailed("Google didn't grant Drive access. Try again.")
        } else {
            viewModel.onSignInFailed("Drive access wasn't granted.")
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::export)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importUri = uri }

    fun turnOn() {
        viewModel.onSigningIn()
        scope.launch {
            var email: String? = null
            if (BackupRepository.WEB_CLIENT_ID.isNotBlank()) {
                try {
                    val option = GetGoogleIdOption.Builder()
                        .setServerClientId(BackupRepository.WEB_CLIENT_ID)
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                    val response = CredentialManager.create(context).getCredential(context, GetCredentialRequest(listOf(option)))
                    email = GoogleIdTokenCredential.createFrom(response.credential.data).id
                } catch (e: GetCredentialCancellationException) {
                    viewModel.onSignInFailed("Sign-in was cancelled.")
                    return@launch
                } catch (e: GetCredentialException) {
                    viewModel.onSignInFailed("Google sign-in failed: ${e.message}")
                    return@launch
                }
            }
            pendingEmail = email
            try {
                val auth = GoogleDriveAuth.authorize(context, email)
                val pending = auth.pendingIntent
                if (auth.hasResolution() && pending != null) {
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    viewModel.onAccessGranted(email)
                }
            } catch (e: Exception) {
                viewModel.onSignInFailed("Couldn't ask Google for Drive access: ${e.message}. See the Drive backup steps in docs/SETUP.md.")
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Backup", style = MaterialTheme.typography.titleMedium)
            val status = s.status
            val lastAt = status.lastBackupAt
            Text(
                when {
                    !status.enabled -> "Back up your courses and progress to a hidden folder in your Google Drive, so another phone can restore them. Keys and AI caches are never included."
                    lastAt != null -> "On${status.accountEmail?.let { " for $it" } ?: ""}. Last backup ${DateUtils.getRelativeTimeSpanString(lastAt)}. Backs up daily on Wi-Fi and after each finished chapter."
                    else -> "On${status.accountEmail?.let { " for $it" } ?: ""}. No backup yet."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            status.lastError?.takeIf { status.enabled }?.let {
                Text("Last problem: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            s.busy?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            s.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (s.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            val idle = s.busy == null
            if (!status.enabled) {
                Button(onClick = ::turnOn, enabled = idle, modifier = Modifier.fillMaxWidth()) { Text("Turn on Drive backup") }
            } else {
                Button(onClick = viewModel::backupNow, enabled = idle, modifier = Modifier.fillMaxWidth()) { Text("Back up now") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.enabled) {
                    OutlinedButton(onClick = { restoreSheet = true; viewModel.loadSnapshots() }, enabled = idle) { Text("Restore from Drive") }
                }
                OutlinedButton(onClick = { exporter.launch("learn-anything-backup-${LocalDate.now()}.zip") }, enabled = idle) { Text("Export file") }
                OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = idle) { Text("Import file") }
                if (status.enabled) TextButton(onClick = viewModel::turnOff, enabled = idle) { Text("Turn off") }
            }
        }
    }

    if (restoreSheet) {
        ModalBottomSheet(onDismissRequest = { restoreSheet = false }) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Restore from Drive", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Merge keeps this phone's progress and adds anything newer from the backup. Replace makes this phone match the backup exactly.",
                    style = MaterialTheme.typography.bodySmall,
                )
                when {
                    s.snapshots == null -> CircularProgressIndicator()
                    s.snapshotsError != null -> Text(s.snapshotsError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    s.snapshots!!.isEmpty() -> Text("No backups yet.")
                    else -> LazyColumn {
                        items(s.snapshots!!, key = { it.id }) { snap ->
                            SnapshotRow(
                                snap,
                                onMerge = { restoreSheet = false; viewModel.restore(snap, RestoreMode.MERGE) },
                                onReplace = { confirmReplace = { restoreSheet = false; viewModel.restore(snap, RestoreMode.REPLACE) } },
                            )
                        }
                    }
                }
            }
        }
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("Import this backup?") },
            text = { Text("Merge keeps this phone's progress and adds anything newer from the file. Replace makes this phone match the file exactly.") },
            confirmButton = { TextButton(onClick = { importUri = null; viewModel.import(uri, RestoreMode.MERGE) }) { Text("Merge") } },
            dismissButton = {
                TextButton(onClick = { importUri = null; confirmReplace = { viewModel.import(uri, RestoreMode.REPLACE) } }) { Text("Replace") }
            },
        )
    }

    confirmReplace?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmReplace = null },
            title = { Text("Replace everything on this phone?") },
            text = { Text("Courses and progress on this phone that aren't in the backup will be removed. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmReplace = null; action() }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirmReplace = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SnapshotRow(snap: Snapshot, onMerge: () -> Unit, onReplace: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(DateUtils.formatDateTime(null, snap.createdAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR))
        },
        supportingContent = { snap.sizeBytes?.let { Text("${it / 1024 + 1} KB") } },
        trailingContent = {
            Column {
                TextButton(onClick = onMerge) { Text("Merge") }
                TextButton(onClick = onReplace) { Text("Replace") }
            }
        },
    )
}
