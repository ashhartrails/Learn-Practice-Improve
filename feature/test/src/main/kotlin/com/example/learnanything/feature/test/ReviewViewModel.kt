package com.example.learnanything.feature.test

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.ReviewItem
import com.example.learnanything.core.data.usecase.ReviewAnswerUseCase
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.GradeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val loading: Boolean = true,
    val items: List<ReviewItem> = emptyList(),
    val index: Int = 0,
    val answer: Answer? = null,
    val result: GradeResult? = null,
    val correctCount: Int = 0,
) {
    val current: ReviewItem? get() = items.getOrNull(index)
    val done: Boolean get() = !loading && index >= items.size
}

/** Spaced-repetition review: stored questions, graded by code, no AI calls. */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val learning: LearningRepository,
    private val reviewAnswer: ReviewAnswerUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = ReviewUiState(loading = false, items = learning.dueReviews()) }
    }

    fun onAnswer(answer: Answer) = _state.update { if (it.result == null) it.copy(answer = answer) else it }

    fun check() {
        val s = _state.value
        val item = s.current ?: return
        val answer = s.answer ?: return
        if (s.result != null) return
        viewModelScope.launch {
            val result = reviewAnswer(item.item, item.topicTitle, answer)
            _state.update { it.copy(result = result, correctCount = it.correctCount + if (result.correct) 1 else 0) }
        }
    }

    fun next() = _state.update { it.copy(index = it.index + 1, answer = null, result = null) }
}
