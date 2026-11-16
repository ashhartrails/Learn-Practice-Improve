package com.example.learnanything.feature.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.AttemptContext
import com.example.learnanything.core.data.repository.StoredItem
import com.example.learnanything.core.data.repository.StoredLesson
import com.example.learnanything.core.data.usecase.FinishTopicUseCase
import com.example.learnanything.core.data.usecase.GradeAnswerUseCase
import com.example.learnanything.core.data.usecase.LessonResult
import com.example.learnanything.core.data.usecase.OpenLessonUseCase
import com.example.learnanything.core.data.usecase.ProgressText
import com.example.learnanything.core.data.usecase.TopicPlace
import com.example.learnanything.core.data.usecase.UserMessage
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.GradeResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TopicUiState {
    data class Loading(val progress: String) : TopicUiState
    data class Failed(val message: UserMessage) : TopicUiState
    data class Ready(
        val place: TopicPlace,
        val lesson: StoredLesson,
        val answers: Map<String, Answer> = emptyMap(),
        val results: Map<String, GradeResult> = emptyMap(),
        val finished: Boolean = false,
    ) : TopicUiState {
        val allChecksAnswered: Boolean get() = lesson.checks.all { it.id in results }
        val checksCorrect: Int get() = lesson.checks.count { results[it.id]?.correct == true }
    }
}

@HiltViewModel(assistedFactory = TopicViewModel.Factory::class)
class TopicViewModel @AssistedInject constructor(
    @Assisted private val topicId: String,
    private val openLesson: OpenLessonUseCase,
    private val grade: GradeAnswerUseCase,
    private val finishTopic: FinishTopicUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow<TopicUiState>(TopicUiState.Loading("Opening…"))
    val state: StateFlow<TopicUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = TopicUiState.Loading("Opening…")
        viewModelScope.launch {
            val result = openLesson(topicId) { p -> _state.value = TopicUiState.Loading(ProgressText.of(p, "your lesson")) }
            _state.value = when (result) {
                is LessonResult.Ready -> TopicUiState.Ready(result.place, result.lesson)
                is LessonResult.Failed -> TopicUiState.Failed(result.message)
            }
        }
    }

    fun onAnswer(itemId: String, answer: Answer) = updateReady { it.copy(answers = it.answers + (itemId to answer)) }

    /** Grades by code. Check questions get one try; the try-it question can be retried. */
    fun check(item: StoredItem) {
        val ready = _state.value as? TopicUiState.Ready ?: return
        val answer = ready.answers[item.id] ?: return
        if (item.id in ready.results) return
        viewModelScope.launch {
            val result = grade(item, answer, AttemptContext.LESSON)
            updateReady { it.copy(results = it.results + (item.id to result)) }
        }
    }

    fun retryTryIt() = updateReady { s ->
        val id = s.lesson.tryIt.id
        s.copy(results = s.results - id, answers = s.answers - id)
    }

    fun finish() {
        val ready = _state.value as? TopicUiState.Ready ?: return
        if (!ready.allChecksAnswered || ready.finished) return
        viewModelScope.launch {
            finishTopic(ready.place.topic, ready.lesson.checks.map { ready.results.getValue(it.id).correct })
            updateReady { it.copy(finished = true) }
        }
    }

    private fun updateReady(transform: (TopicUiState.Ready) -> TopicUiState.Ready) =
        _state.update { if (it is TopicUiState.Ready) transform(it) else it }

    @AssistedFactory
    interface Factory {
        fun create(topicId: String): TopicViewModel
    }
}
