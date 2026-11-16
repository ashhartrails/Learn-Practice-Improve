package com.example.learnanything.feature.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.StoredItem
import com.example.learnanything.core.data.usecase.GetQuizUseCase
import com.example.learnanything.core.data.usecase.ProgressText
import com.example.learnanything.core.data.usecase.QuizOutcome
import com.example.learnanything.core.data.usecase.QuizPlace
import com.example.learnanything.core.data.usecase.QuizResult
import com.example.learnanything.core.data.usecase.SubmitQuizUseCase
import com.example.learnanything.core.data.usecase.UserMessage
import com.example.learnanything.core.model.question.Answer
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface QuizUiState {
    data class Loading(val progress: String) : QuizUiState
    data class Failed(val message: UserMessage) : QuizUiState
    data class Taking(
        val place: QuizPlace,
        val items: List<StoredItem>,
        val index: Int = 0,
        val answers: Map<String, Answer> = emptyMap(),
        val submitting: Boolean = false,
    ) : QuizUiState {
        val current: StoredItem get() = items[index]
        val isLast: Boolean get() = index == items.lastIndex
    }
    data class Done(val place: QuizPlace, val outcome: QuizOutcome) : QuizUiState
}

@HiltViewModel(assistedFactory = QuizViewModel.Factory::class)
class QuizViewModel @AssistedInject constructor(
    @Assisted private val chapterId: String,
    private val getQuiz: GetQuizUseCase,
    private val submitQuiz: SubmitQuizUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<QuizUiState>(QuizUiState.Loading("Opening…"))
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    init { load(newQuestions = false) }

    fun load(newQuestions: Boolean) {
        _state.value = QuizUiState.Loading(if (newQuestions) "Writing new questions…" else "Opening…")
        viewModelScope.launch {
            val result = getQuiz(chapterId, newQuestions) { p -> _state.value = QuizUiState.Loading(ProgressText.of(p, "your quiz")) }
            _state.value = when (result) {
                is QuizResult.Ready -> QuizUiState.Taking(result.place, result.items)
                is QuizResult.Failed -> QuizUiState.Failed(result.message)
            }
        }
    }

    fun onAnswer(answer: Answer) = updateTaking { it.copy(answers = it.answers + (it.current.id to answer)) }
    fun next() = updateTaking { if (it.isLast) it else it.copy(index = it.index + 1) }
    fun previous() = updateTaking { if (it.index == 0) it else it.copy(index = it.index - 1) }

    fun submit() {
        val taking = _state.value as? QuizUiState.Taking ?: return
        if (taking.submitting) return
        updateTaking { it.copy(submitting = true) }
        viewModelScope.launch {
            val outcome = submitQuiz(taking.place, taking.items, taking.answers)
            _state.value = QuizUiState.Done(taking.place, outcome)
        }
    }

    private fun updateTaking(transform: (QuizUiState.Taking) -> QuizUiState.Taking) =
        _state.update { if (it is QuizUiState.Taking) transform(it) else it }

    @AssistedFactory
    interface Factory {
        fun create(chapterId: String): QuizViewModel
    }
}
