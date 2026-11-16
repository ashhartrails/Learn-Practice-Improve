package com.example.learnanything.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.learnanything.core.ai.gemini.GeminiSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class GeminiKeyState(val saved: Boolean, val lastFour: String?)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) : GeminiSettings {

    val geminiKey: Flow<GeminiKeyState> = dataStore.data.map {
        GeminiKeyState(saved = it[GEMINI_KEY] != null, lastFour = it[GEMINI_KEY_HINT])
    }

    val geminiModel: Flow<String?> = dataStore.data.map { it[GEMINI_MODEL] }

    suspend fun saveGeminiKey(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "Key is empty" }
        val encrypted = cipher.encrypt(trimmed)
        dataStore.edit {
            it[GEMINI_KEY] = encrypted
            it[GEMINI_KEY_HINT] = trimmed.takeLast(4)
        }
    }

    suspend fun clearGeminiKey() {
        dataStore.edit {
            it.remove(GEMINI_KEY)
            it.remove(GEMINI_KEY_HINT)
            it.remove(GEMINI_MODEL)
        }
    }

    suspend fun setGeminiModel(modelId: String?) {
        dataStore.edit { if (modelId == null) it.remove(GEMINI_MODEL) else it[GEMINI_MODEL] = modelId }
    }

    override suspend fun apiKey(): String? = dataStore.data.first()[GEMINI_KEY]?.let(cipher::decrypt)

    override suspend fun selectedModel(): String? = dataStore.data.first()[GEMINI_MODEL]

    private companion object {
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key_enc")
        val GEMINI_KEY_HINT = stringPreferencesKey("gemini_api_key_last4")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
    }
}
