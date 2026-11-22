package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.ai.ModelRanker
import com.example.learnanything.core.ai.gemini.GeminiProvider
import com.example.learnanything.core.data.settings.SettingsRepository
import javax.inject.Inject

sealed interface KeyCheckResult {
    data class Saved(val models: List<ModelInfo>) : KeyCheckResult
    /** Saved, but we couldn't reach Google to confirm it. */
    data class SavedUnverified(val reason: String) : KeyCheckResult
    data class Rejected(val reason: String) : KeyCheckResult
}

/** Settings actions for the Gemini provider: verify and save a key, list and choose models. */
class GeminiSetupUseCase @Inject constructor(
    private val gemini: GeminiProvider,
    private val settings: SettingsRepository,
) {
    suspend fun verifyAndSave(key: String): KeyCheckResult {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return KeyCheckResult.Rejected("Enter a key first.")
        return try {
            val models = gemini.listModelsWithKey(trimmed)
            settings.saveGeminiKey(trimmed)
            gemini.invalidateModelCache()
            KeyCheckResult.Saved(ModelRanker.rank(models))
        } catch (e: AiException.Unauthorized) {
            KeyCheckResult.Rejected("Google rejected this key. Check you copied all of it.")
        } catch (e: AiException.Network) {
            settings.saveGeminiKey(trimmed)
            gemini.invalidateModelCache()
            KeyCheckResult.SavedUnverified("Couldn't reach Google to check the key. It's saved; try again when online.")
        } catch (e: AiException) {
            KeyCheckResult.Rejected(e.message ?: "The key check failed.")
        }
    }

    /** Text models for the saved key, best first. */
    suspend fun rankedModels(): List<ModelInfo> = ModelRanker.rank(gemini.listModels())

    suspend fun selectModel(modelId: String?) = settings.setGeminiModel(modelId)

    suspend fun removeKey() {
        settings.clearGeminiKey()
        gemini.invalidateModelCache()
    }
}
