package com.example.learnanything.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.ai.FailureKind
import com.example.learnanything.core.data.usecase.GenerateOutlineUseCase
import com.example.learnanything.core.data.usecase.OutlineResult
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.outline.OutlineRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateUiState(
    val goal: String = "",
    val level: Level = Level.BEGINNER,
    val weeklyHours: Int = 3,
    val generating: Boolean = false,
    val progress: String? = null,
    val error: CreateError? = null,
    /** Set once the outline is saved; the screen navigates and calls [CreateSubjectViewModel.onNavigated]. */
    val createdSubjectId: String? = null,
) {
    val canSubmit: Boolean get() = goal.trim().length >= 3 && !generating
}

data class CreateError(val message: String, val needsSettings: Boolean)

@HiltViewModel
class CreateSubjectViewModel @Inject constructor(
    private val generateOutline: GenerateOutlineUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onGoalChange(value: String) = _state.update { it.copy(goal = value.take(200), error = null) }
    fun onLevelChange(level: Level) = _state.update { it.copy(level = level) }
    fun onHoursChange(hours: Int) = _state.update { it.copy(weeklyHours = hours.coerceIn(1, 20)) }

    fun generate() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(generating = true, error = null, progress = "Starting…") }
        job = viewModelScope.launch {
            val request = OutlineRequest(current.goal.trim(), current.level, current.weeklyHours)
            val result = generateOutline(request) { p ->
                val retry = if (p.attempt > 1) " (fixing a problem and retrying)" else ""
                val text = if (p.charsReceived == 0) "Asking ${p.providerName}$retry…"
                else "Writing your course$retry… ${p.charsReceived} characters"
                _state.update { it.copy(progress = text) }
            }
            _state.update {
                when (result) {
                    is OutlineResult.Saved -> it.copy(generating = false, progress = null, createdSubjectId = result.subjectId)
                    is OutlineResult.Failed -> it.copy(generating = false, progress = null, error = result.toError())
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(generating = false, progress = null) }
    }

    fun onNavigated() = _state.update { CreateUiState() }

    private fun OutlineResult.Failed.toError(): CreateError = when (kind) {
        FailureKind.NO_PROVIDER -> CreateError("Add your free Gemini API key in Settings first.", needsSettings = true)
        FailureKind.UNAUTHORIZED -> CreateError("Gemini rejected your API key. Check it in Settings.", needsSettings = true)
        FailureKind.RATE_LIMITED -> CreateError(
            "You've hit Gemini's free-tier limit." +
                (retryAfterSeconds?.let { " Try again in about $it seconds." } ?: " Try again in a minute."),
            needsSettings = false,
        )
        FailureKind.NETWORK -> CreateError("No connection. Check your internet and try again.", needsSettings = false)
        FailureKind.BLOCKED -> CreateError("Gemini declined this request. Try rewording your goal.", needsSettings = false)
        FailureKind.INVALID_OUTPUT -> CreateError("The AI's course didn't pass our checks twice. Try again.", needsSettings = false)
        FailureKind.PROVIDER_ERROR -> CreateError(message, needsSettings = message.contains("404"))
    }
}
