package com.example.learnanything.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class BackupStatus(
    val enabled: Boolean = false,
    val accountEmail: String? = null,
    val lastBackupAt: Long? = null,
    val lastError: String? = null,
)

@Singleton
class BackupPrefs @Inject constructor(private val dataStore: DataStore<Preferences>) {
    val status: Flow<BackupStatus> = dataStore.data.map {
        BackupStatus(it[ENABLED] == true, it[EMAIL], it[LAST_AT], it[LAST_ERROR])
    }

    suspend fun accountEmail(): String? = dataStore.data.first()[EMAIL]
    suspend fun isEnabled(): Boolean = dataStore.data.first()[ENABLED] == true

    suspend fun enable(email: String?) {
        dataStore.edit {
            it[ENABLED] = true
            if (email.isNullOrBlank()) it.remove(EMAIL) else it[EMAIL] = email
            it.remove(LAST_ERROR)
        }
    }

    suspend fun disable() {
        dataStore.edit {
            it[ENABLED] = false
            it.remove(EMAIL)
        }
    }

    suspend fun recordSuccess(at: Long) {
        dataStore.edit {
            it[LAST_AT] = at
            it.remove(LAST_ERROR)
        }
    }

    suspend fun recordError(message: String) {
        dataStore.edit { it[LAST_ERROR] = message }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("backup_enabled")
        val EMAIL = stringPreferencesKey("backup_account_email")
        val LAST_AT = longPreferencesKey("backup_last_at")
        val LAST_ERROR = stringPreferencesKey("backup_last_error")
    }
}
