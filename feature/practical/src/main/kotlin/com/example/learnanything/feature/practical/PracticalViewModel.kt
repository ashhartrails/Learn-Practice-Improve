package com.example.learnanything.feature.practical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.StoredPractical
import com.example.learnanything.core.data.usecase.GetPracticalUseCase
import com.example.learnanything.core.data.usecase.PracticalLoad
import com.example.learnanything.core.data.usecase.PracticalOutcome
import com.example.learnanything.core.data.usecase.ProgressText
import com.example.learnanything.core.data.usecase.QuizPlace
import com.example.learnanything.core.data.usecase.SubmitPracticalUseCase
import com.example.learnanything.core.data.usecase.UserMessage
import com.example.learnanything.core.model.practical.PracticalKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PracticalUiState {
    data class Loading(val progress: String) : PracticalUiState
    data class Failed(val message: UserMessage) : PracticalUiState
    data class Working(
        val place: QuizPlace,
        val stored: StoredPractical,
        val answer: String,
        val checking: Boolean = false,
        val checkingText: String = "",
        val outcome: PracticalOutcome.Graded? = null,
        val error: UserMessage? = null,
    ) : PracticalUiState
}

@HiltViewModel(assistedFactory = PracticalViewModel.Factory::class)
class PracticalViewModel @AssistedInject constructor(
    @Assisted private val chapterId: String,
    private val getPractical: GetPracticalUseCase,
    private val submitPractical: SubmitPracticalUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<PracticalUiState>(PracticalUiState.Loading("Opening…"))
    val state: StateFlow<PracticalUiState> = _state.asStateFlow()

    init { load(newTask = false, kind = null) }

    /** [kind] asks for a different type of practical; [newTask] asks for a fresh one of any type. */
    fun load(newTask: Boolean, kind: PracticalKind?) {
        _state.value = PracticalUiState.Loading(if (newTask || kind != null) "Writing a new practical…" else "Opening…")
        viewModelScope.launch {
            val result = getPractical(chapterId, newTask, kind) { p ->
                _state.value = PracticalUiState.Loading(ProgressText.of(p, "your practical"))
            }
            _state.value = when (result) {
                is PracticalLoad.Ready -> PracticalUiState.Working(
                    result.place,
                    result.stored,
                    answer = result.lastAnswer ?: result.stored.practical.starterCode,
                )
                is PracticalLoad.Failed -> PracticalUiState.Failed(result.message)
            }
        }
    }

    fun onAnswer(text: String) = update { it.copy(answer = text, error = null) }

    fun submit() {
        val s = _state.value as? PracticalUiState.Working ?: return
        if (s.checking) return
        val text = when (s.stored.practical.kind) {
            PracticalKind.WRITTEN -> "Grading your answer…"
            PracticalKind.NUMERIC -> "Checking…"
            PracticalKind.PYTHON -> "Running the tests (the first run starts Python, which can take a little while)…"
            else -> "Running the tests…"
        }
        update { it.copy(checking = true, checkingText = text, error = null, outcome = null) }
        viewModelScope.launch {
            val outcome = submitPractical(s.place, s.stored, s.answer) { p ->
                update { it.copy(checkingText = ProgressText.of(p, "your grade")) }
            }
            update {
                when (outcome) {
                    is PracticalOutcome.Graded -> it.copy(checking = false, outcome = outcome)
                    is PracticalOutcome.Failed -> it.copy(checking = false, error = outcome.message)
                }
            }
        }
    }

    private fun update(transform: (PracticalUiState.Working) -> PracticalUiState.Working) =
        _state.update { if (it is PracticalUiState.Working) transform(it) else it }

    @AssistedFactory
    interface Factory {
        fun create(chapterId: String): PracticalViewModel
    }
}
