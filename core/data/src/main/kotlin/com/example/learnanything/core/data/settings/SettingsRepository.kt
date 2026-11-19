package com.example.learnanything.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.learnanything.core.ai.gemini.GeminiSettings
import com.example.learnanything.core.ai.youtube.YouTubeSettings
import com.example.learnanything.core.ai.openai.CompatibleSettings
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
) : GeminiSettings, YouTubeSettings, CompatibleSettings {

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

    val youTubeKeyState: Flow<GeminiKeyState> = dataStore.data.map {
        GeminiKeyState(saved = it[YOUTUBE_KEY] != null, lastFour = it[YOUTUBE_KEY_HINT])
    }

    suspend fun saveYouTubeKey(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "Key is empty" }
        val encrypted = cipher.encrypt(trimmed)
        dataStore.edit {
            it[YOUTUBE_KEY] = encrypted
            it[YOUTUBE_KEY_HINT] = trimmed.takeLast(4)
        }
    }

    suspend fun clearYouTubeKey() {
        dataStore.edit {
            it.remove(YOUTUBE_KEY)
            it.remove(YOUTUBE_KEY_HINT)
        }
    }

    // --- OpenAI-compatible providers (Groq, OpenRouter), keyed by provider id.

    fun compatibleKey(providerId: String): Flow<GeminiKeyState> = dataStore.data.map {
        GeminiKeyState(saved = it[keyOf(providerId)] != null, lastFour = it[hintOf(providerId)])
    }

    fun compatibleModel(providerId: String): Flow<String?> = dataStore.data.map { it[modelOf(providerId)] }

    suspend fun saveCompatibleKey(providerId: String, key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "Key is empty" }
        val encrypted = cipher.encrypt(trimmed)
        dataStore.edit {
            it[keyOf(providerId)] = encrypted
            it[hintOf(providerId)] = trimmed.takeLast(4)
        }
    }

    suspend fun clearCompatibleKey(providerId: String) {
        dataStore.edit {
            it.remove(keyOf(providerId))
            it.remove(hintOf(providerId))
            it.remove(modelOf(providerId))
        }
    }

    suspend fun setCompatibleModel(providerId: String, modelId: String?) {
        dataStore.edit { if (modelId == null) it.remove(modelOf(providerId)) else it[modelOf(providerId)] = modelId }
    }

    override suspend fun apiKey(providerId: String): String? = dataStore.data.first()[keyOf(providerId)]?.let(cipher::decrypt)

    override suspend fun selectedModel(providerId: String): String? = dataStore.data.first()[modelOf(providerId)]

    private fun keyOf(id: String) = stringPreferencesKey("${id}_api_key_enc")
    private fun hintOf(id: String) = stringPreferencesKey("${id}_api_key_last4")
    private fun modelOf(id: String) = stringPreferencesKey("${id}_model")

    override suspend fun youTubeKey(): String? = dataStore.data.first()[YOUTUBE_KEY]?.let(cipher::decrypt)

    override suspend fun apiKey(): String? = dataStore.data.first()[GEMINI_KEY]?.let(cipher::decrypt)

    override suspend fun selectedModel(): String? = dataStore.data.first()[GEMINI_MODEL]

    private companion object {
        val GEMINI_KEY = stringPreferencesKey("gemini_api_key_enc")
        val GEMINI_KEY_HINT = stringPreferencesKey("gemini_api_key_last4")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val YOUTUBE_KEY = stringPreferencesKey("youtube_api_key_enc")
        val YOUTUBE_KEY_HINT = stringPreferencesKey("youtube_api_key_last4")
    }
}
