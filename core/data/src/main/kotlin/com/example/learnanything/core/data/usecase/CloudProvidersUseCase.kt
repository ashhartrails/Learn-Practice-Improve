package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.AiProvider
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.ai.openai.OpenAiCompatibleProvider
import com.example.learnanything.core.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Extra free cloud providers (Groq, OpenRouter): check and save keys, list and pick models. */
@Singleton
class CloudProvidersUseCase @Inject constructor(
    providers: List<@JvmSuppressWildcards AiProvider>,
    private val settings: SettingsRepository,
) {
    /** The OpenAI-compatible providers, in fallback order (after Gemini). */
    val providers: List<OpenAiCompatibleProvider> = providers.filterIsInstance<OpenAiCompatibleProvider>()

    private fun provider(id: String) = providers.first { it.id == id }

    fun keyState(id: String) = settings.compatibleKey(id)
    fun selectedModel(id: String) = settings.compatibleModel(id)

    /** Tries the key against the provider's live model list before saving it. */
    suspend fun verifyAndSave(id: String, key: String): KeyCheckResult {
        val p = provider(id)
        return try {
            val models = p.listModelsWithKey(key.trim())
            if (models.isEmpty()) return KeyCheckResult.Rejected("${p.displayName} accepted the key but offers no free chat model right now.")
            settings.saveCompatibleKey(id, key)
            p.clearModelCache()
            KeyCheckResult.Saved(models)
        } catch (e: AiException.Unauthorized) {
            KeyCheckResult.Rejected("${p.displayName} rejected this key. Copy it again, with nothing cut off.")
        } catch (e: AiException.Network) {
            settings.saveCompatibleKey(id, key)
            KeyCheckResult.SavedUnverified("Saved, but couldn't reach ${p.displayName} to test it. It will be tried when needed.")
        } catch (e: AiException) {
            KeyCheckResult.Rejected(e.message ?: "${p.displayName} couldn't check the key.")
        }
    }

    suspend fun models(id: String): List<ModelInfo> = provider(id).listModels()

    suspend fun selectModel(id: String, modelId: String?) = settings.setCompatibleModel(id, modelId)

    suspend fun remove(id: String) {
        settings.clearCompatibleKey(id)
        provider(id).clearModelCache()
    }
}
