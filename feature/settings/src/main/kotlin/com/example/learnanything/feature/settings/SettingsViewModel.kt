package com.example.learnanything.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.data.settings.SettingsRepository
import com.example.learnanything.core.data.usecase.GeminiSetupUseCase
import com.example.learnanything.core.data.usecase.KeyCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val keySaved: Boolean = false,
    val keyLastFour: String? = null,
    val keyInput: String = "",
    val checkingKey: Boolean = false,
    val keyMessage: String? = null,
    val keyMessageIsError: Boolean = false,
    val models: List<ModelInfo> = emptyList(),
    val loadingModels: Boolean = false,
    val modelsError: String? = null,
    /** Null means "Automatic": the app picks the best model from the live list. */
    val selectedModelId: String? = null,
) {
    val automaticModel: ModelInfo? get() = models.firstOrNull()
    val selectedModel: ModelInfo? get() = models.firstOrNull { it.id == selectedModelId }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val gemini: GeminiSetupUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.geminiKey, settings.geminiModel) { key, model -> key to model }
                .collect { (key, model) ->
                    val wasSaved = _state.value.keySaved
                    _state.update { it.copy(keySaved = key.saved, keyLastFour = key.lastFour, selectedModelId = model) }
                    if (key.saved && !wasSaved && _state.value.models.isEmpty()) loadModels()
                    if (!key.saved) _state.update { it.copy(models = emptyList()) }
                }
        }
    }

    fun onKeyInput(value: String) = _state.update { it.copy(keyInput = value.trim(), keyMessage = null) }

    fun saveKey() {
        val key = _state.value.keyInput
        _state.update { it.copy(checkingKey = true, keyMessage = null) }
        viewModelScope.launch {
            val result = gemini.verifyAndSave(key)
            _state.update {
                when (result) {
                    is KeyCheckResult.Saved -> it.copy(
                        checkingKey = false, keyInput = "", models = result.models, modelsError = null,
                        keyMessage = "Key works. ${result.models.size} models available.", keyMessageIsError = false,
                    )
                    is KeyCheckResult.SavedUnverified -> it.copy(
                        checkingKey = false, keyInput = "", keyMessage = result.reason, keyMessageIsError = true,
                    )
                    is KeyCheckResult.Rejected -> it.copy(
                        checkingKey = false, keyMessage = result.reason, keyMessageIsError = true,
                    )
                }
            }
        }
    }

    fun removeKey() {
        viewModelScope.launch {
            gemini.removeKey()
            _state.update { it.copy(keyMessage = "Key removed.", keyMessageIsError = false) }
        }
    }

    fun loadModels() {
        _state.update { it.copy(loadingModels = true, modelsError = null) }
        viewModelScope.launch {
            try {
                val models = gemini.rankedModels()
                _state.update { it.copy(models = models, loadingModels = false) }
            } catch (e: AiException) {
                val message = when (e) {
                    is AiException.RateLimited -> "Rate limit reached. Try again shortly."
                    is AiException.Network -> "Offline. Connect to load models."
                    else -> e.message ?: "Couldn't load models."
                }
                _state.update { it.copy(loadingModels = false, modelsError = message) }
            }
        }
    }

    fun selectModel(modelId: String?) {
        viewModelScope.launch { gemini.selectModel(modelId) }
    }
}
