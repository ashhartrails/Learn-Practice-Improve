package com.example.learnanything.feature.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.EnrichmentRepository
import com.example.learnanything.core.data.repository.StoredItem
import com.example.learnanything.core.data.usecase.GetRescueUseCase
import com.example.learnanything.core.data.usecase.GradeTeachBackUseCase
import com.example.learnanything.core.data.usecase.ProgressText
import com.example.learnanything.core.data.usecase.RescueCheckUseCase
import com.example.learnanything.core.data.usecase.RescueContent
import com.example.learnanything.core.data.usecase.TeachBackOutcome
import com.example.learnanything.core.data.usecase.UserMessage
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.AnswerChecker
import com.example.learnanything.core.model.question.GradeResult
import com.example.learnanything.core.model.question.toQuestion
import com.example.learnanything.core.model.rescue.RescueRules
import com.example.learnanything.core.model.rescue.RescueRung
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RescueUiState(
    val order: List<RescueRung> = emptyList(),
    val index: Int = 0,
    val loading: String? = "Opening…",
    val content: RescueContent? = null,
    /** Answers to the rung's own exercises (blanks, Socratic and prerequisite questions), by key. */
    val inner: Map<String, Answer> = emptyMap(),
    val innerResults: Map<String, GradeResult> = emptyMap(),
    val blanks: List<String> = emptyList(),
    val blankResults: List<Boolean>? = null,
    val teachBack: String = "",
    val teachBackGrading: Boolean = false,
    val teachBackOutcome: TeachBackOutcome? = null,
    /** The lesson check question that decides whether this rung helped. */
    val check: StoredItem? = null,
    val checkAnswer: Answer? = null,
    val checkResult: GradeResult? = null,
    val helped: Boolean = false,
    val outOfRungs: Boolean = false,
    val message: UserMessage? = null,
) {
    val rung: RescueRung? get() = order.getOrNull(index)
}

@HiltViewModel(assistedFactory = RescueViewModel.Factory::class)
class RescueViewModel @AssistedInject constructor(
    @Assisted("topicId") private val topicId: String,
    @Assisted("startRung") private val startRung: String?,
    private val getRescue: GetRescueUseCase,
    private val rescueCheck: RescueCheckUseCase,
    private val gradeTeachBack: GradeTeachBackUseCase,
    private val enrichment: EnrichmentRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(RescueUiState())
    val state: StateFlow<RescueUiState> = _state.asStateFlow()
    private var attempts = 0

    init {
        viewModelScope.launch {
            val learned = enrichment.ladderOrder()
            val start = RescueRung.entries.firstOrNull { it.name == startRung }
            val order = if (start != null) listOf(start) + learned.filter { it != start } else learned
            _state.update { it.copy(order = order, index = 0) }
            loadRung()
        }
    }

    private fun loadRung() {
        val rung = _state.value.rung ?: return
        _state.update {
            RescueUiState(order = it.order, index = it.index, loading = "Preparing: ${rung.label.lowercase()}…")
        }
        viewModelScope.launch {
            val content = getRescue(topicId, rung) { p -> _state.update { it.copy(loading = ProgressText.of(p, rung.label.lowercase())) } }
            val check = rescueCheck.question(topicId, attempts)
            _state.update {
                it.copy(
                    loading = null,
                    content = content,
                    check = check,
                    blanks = (content as? RescueContent.Written)?.content?.blanks?.map { "" }.orEmpty(),
                )
            }
        }
    }

    // --- The rung's own exercises (code-checked, not recorded: they're practice).

    fun onInnerAnswer(key: String, answer: Answer) = _state.update { it.copy(inner = it.inner + (key to answer)) }

    fun checkInner(key: String, dto: com.example.learnanything.core.model.question.QuestionDto) {
        val answer = _state.value.inner[key] ?: return
        val result = AnswerChecker.check(dto.toQuestion(), answer)
        _state.update { it.copy(innerResults = it.innerResults + (key to result)) }
    }

    fun onBlank(i: Int, text: String) = _state.update { s ->
        s.copy(blanks = s.blanks.toMutableList().also { if (i in it.indices) it[i] = text }, blankResults = null)
    }

    fun checkBlanks() {
        val written = (_state.value.content as? RescueContent.Written)?.content ?: return
        _state.update { it.copy(blankResults = RescueRules.checkBlanks(written, it.blanks)) }
    }

    fun onTeachBack(text: String) = _state.update { it.copy(teachBack = text, teachBackOutcome = null) }

    fun gradeTeachBack() {
        val written = (_state.value.content as? RescueContent.Written)?.content ?: return
        val text = _state.value.teachBack
        _state.update { it.copy(teachBackGrading = true, teachBackOutcome = null) }
        viewModelScope.launch {
            val outcome = gradeTeachBack(written, text)
            _state.update { it.copy(teachBackGrading = false, teachBackOutcome = outcome) }
        }
    }

    // --- Did it help? One lesson check question decides.

    fun onCheckAnswer(answer: Answer) = _state.update { it.copy(checkAnswer = answer) }

    fun submitCheck() {
        val s = _state.value
        val rung = s.rung ?: return
        val item = s.check ?: return
        val answer = s.checkAnswer ?: return
        if (s.checkResult != null) return
        viewModelScope.launch {
            val result = rescueCheck.answer(topicId, rung, item, answer)
            attempts++
            _state.update { it.copy(checkResult = result, helped = result.correct) }
        }
    }

    /** Next rung. If the learner didn't try the question, the rung is recorded as not helping. */
    fun next() {
        val s = _state.value
        val rung = s.rung ?: return
        viewModelScope.launch {
            if (s.checkResult == null) rescueCheck.skip(topicId, rung)
            if (s.index + 1 >= s.order.size) {
                _state.update { it.copy(outOfRungs = true, loading = null) }
            } else {
                _state.update { it.copy(index = it.index + 1) }
                loadRung()
            }
        }
    }

    /** Thumbs up keeps the video; thumbs down forgets it and finds another. */
    fun rateVideo(up: Boolean) {
        val video = _state.value.content as? RescueContent.Video ?: return
        viewModelScope.launch {
            enrichment.rate(topicId, video.resource, if (up) 1 else -1)
            if (!up) loadRung() else _state.update { it.copy(message = UserMessage("Thanks. This video is kept for this topic.")) }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted("topicId") topicId: String, @Assisted("startRung") startRung: String?): RescueViewModel
    }
}
