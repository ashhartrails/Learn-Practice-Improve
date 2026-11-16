package com.example.learnanything.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.usecase.ClarifyGoalUseCase
import com.example.learnanything.core.data.usecase.ClarifyResult
import com.example.learnanything.core.data.usecase.FailureMessages
import com.example.learnanything.core.data.usecase.GenerateOutlineUseCase
import com.example.learnanything.core.data.usecase.OutlineResult
import com.example.learnanything.core.data.usecase.ProgressText
import com.example.learnanything.core.data.usecase.UserMessage
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.clarify.Clarification
import com.example.learnanything.core.model.clarify.ClarifyQuestion
import com.example.learnanything.core.model.outline.OutlineRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CreateStep { GOAL, ASKING, CLARIFY, BUILDING }

data class CreateUiState(
    val goal: String = "",
    val level: Level = Level.BEGINNER,
    val weeklyHours: Int = 3,
    val step: CreateStep = CreateStep.GOAL,
    val questions: List<ClarifyQuestion> = emptyList(),
    /** Question id to the learner's answer. */
    val answers: Map<String, String> = emptyMap(),
    val progress: String? = null,
    val error: UserMessage? = null,
    /** The clarify call failed; offer to build without questions. */
    val canSkipQuestions: Boolean = false,
    /** Set once the draft outline is saved; the screen opens the editor. */
    val createdSubjectId: String? = null,
) {
    val canContinue: Boolean get() = goal.trim().length >= 3 && step == CreateStep.GOAL
}

@HiltViewModel
class CreateSubjectViewModel @Inject constructor(
    private val clarify: ClarifyGoalUseCase,
    private val generateOutline: GenerateOutlineUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onGoalChange(value: String) = _state.update { it.copy(goal = value.take(200), error = null, canSkipQuestions = false) }
    fun onLevelChange(level: Level) = _state.update { it.copy(level = level) }
    fun onHoursChange(hours: Int) = _state.update { it.copy(weeklyHours = hours.coerceIn(1, 20)) }
    fun onAnswer(questionId: String, answer: String) =
        _state.update { it.copy(answers = it.answers + (questionId to answer.take(200))) }

    private fun request() = _state.value.let { OutlineRequest(it.goal.trim(), it.level, it.weeklyHours) }

    /** Step 1 → 2: ask the AI for clarifying questions. */
    fun askQuestions() {
        if (!_state.value.canContinue) return
        _state.update { it.copy(step = CreateStep.ASKING, error = null, progress = "Thinking about your goal…") }
        job = viewModelScope.launch {
            val result = clarify(request()) { p -> _state.update { it.copy(progress = ProgressText.of(p, "questions")) } }
            _state.update {
                when (result) {
                    is ClarifyResult.Questions -> it.copy(step = CreateStep.CLARIFY, questions = result.questions, progress = null)
                    is ClarifyResult.Failed -> it.copy(
                        step = CreateStep.GOAL, progress = null, error = result.message,
                        canSkipQuestions = !result.message.needsSettings,
                    )
                }
            }
        }
    }

    /** Step 2 → 3: build the outline with whatever answers were given. */
    fun buildCourse() = build(withAnswers = true)

    fun skipQuestions() = build(withAnswers = false)

    private fun build(withAnswers: Boolean) {
        val s = _state.value
        val clarifications = if (!withAnswers) emptyList() else s.questions.mapNotNull { q ->
            s.answers[q.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { Clarification(q.question, it) }
        }
        _state.update { it.copy(step = CreateStep.BUILDING, error = null, progress = "Starting…") }
        job = viewModelScope.launch {
            val result = generateOutline(request(), clarifications) { p ->
                _state.update { it.copy(progress = ProgressText.of(p, "your course")) }
            }
            _state.update {
                when (result) {
                    is OutlineResult.Saved -> it.copy(progress = null, createdSubjectId = result.subjectId)
                    is OutlineResult.Failed -> it.copy(
                        step = if (it.questions.isEmpty()) CreateStep.GOAL else CreateStep.CLARIFY,
                        progress = null,
                        error = FailureMessages.of(result.kind, result.message, result.retryAfterSeconds),
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update {
            it.copy(step = if (it.step == CreateStep.BUILDING && it.questions.isNotEmpty()) CreateStep.CLARIFY else CreateStep.GOAL, progress = null)
        }
    }

    fun onNavigated() = _state.update { CreateUiState() }
}
