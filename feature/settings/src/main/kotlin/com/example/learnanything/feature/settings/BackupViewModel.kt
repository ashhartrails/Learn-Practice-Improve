package com.example.learnanything.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.backup.BackupOutcome
import com.example.learnanything.core.data.backup.BackupPrefs
import com.example.learnanything.core.data.backup.BackupRepository
import com.example.learnanything.core.data.backup.BackupScheduler
import com.example.learnanything.core.data.backup.BackupStatus
import com.example.learnanything.core.data.backup.Snapshot
import com.example.learnanything.core.model.backup.RestoreMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val status: BackupStatus = BackupStatus(),
    val busy: String? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val snapshots: List<Snapshot>? = null,
    val snapshotsError: String? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupRepository,
    private val prefs: BackupPrefs,
    private val scheduler: BackupScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { backup.status.collect { s -> _state.update { it.copy(status = s) } } }
    }

    fun onSignInFailed(message: String) = _state.update { it.copy(busy = null, message = message, messageIsError = true) }

    fun onSigningIn() = _state.update { it.copy(busy = "Connecting to Google…", message = null) }

    /** Drive access granted on screen: turn backup on, schedule the daily backup, and back up now. */
    fun onAccessGranted(email: String?) {
        viewModelScope.launch {
            prefs.enable(email)
            scheduler.setDaily(true)
            run("Backing up…") { backup.backupNow() }
        }
    }

    fun backupNow() = viewModelScope.launch { run("Backing up…") { backup.backupNow() } }

    fun turnOff() {
        viewModelScope.launch {
            prefs.disable()
            scheduler.setDaily(false)
            _state.update { it.copy(message = "Drive backup is off. Existing backups stay in your Drive until you remove the app's access in your Google Account.", messageIsError = false) }
        }
    }

    fun loadSnapshots() {
        _state.update { it.copy(snapshots = null, snapshotsError = null) }
        viewModelScope.launch {
            backup.snapshots()
                .onSuccess { list -> _state.update { it.copy(snapshots = list) } }
                .onFailure { e -> _state.update { it.copy(snapshots = emptyList(), snapshotsError = e.message) } }
        }
    }

    fun restore(snapshot: Snapshot, mode: RestoreMode) = viewModelScope.launch {
        run(if (mode == RestoreMode.REPLACE) "Restoring…" else "Merging…") { backup.restore(snapshot, mode) }
    }

    fun export(uri: Uri) = viewModelScope.launch { run("Exporting…") { backup.exportTo(uri) } }

    fun import(uri: Uri, mode: RestoreMode) = viewModelScope.launch {
        run(if (mode == RestoreMode.REPLACE) "Restoring…" else "Merging…") { backup.importFrom(uri, mode) }
    }

    private suspend fun run(label: String, block: suspend () -> BackupOutcome) {
        _state.update { it.copy(busy = label, message = null) }
        val outcome = block()
        _state.update {
            when (outcome) {
                is BackupOutcome.Done -> it.copy(busy = null, message = outcome.message, messageIsError = false)
                BackupOutcome.NeedsAccess -> it.copy(busy = null, message = "Turn on Drive backup first (Google needs your permission).", messageIsError = true)
                is BackupOutcome.Failed -> it.copy(busy = null, message = outcome.message, messageIsError = true)
            }
        }
    }
}
