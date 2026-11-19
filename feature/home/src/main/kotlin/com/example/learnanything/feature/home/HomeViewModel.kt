package com.example.learnanything.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.NextStep
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.SubjectStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ContinueCard(val subject: Subject, val next: NextStep)

sealed interface SubjectsUiState {
    data object Loading : SubjectsUiState
    data class Ready(
        val subjects: List<Subject>,
        val continueCard: ContinueCard? = null,
        val reviewsDue: Int = 0,
    ) : SubjectsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(subjects: SubjectRepository, learning: LearningRepository) : ViewModel() {

    /** The most recently used active course that isn't finished. */
    private val continueCard = subjects.observeSubjects().flatMapLatest { list ->
        val latest = list.firstOrNull { it.status == SubjectStatus.ACTIVE }
            ?: return@flatMapLatest flowOf(null)
        subjects.observeOutline(latest.id).map { outline ->
            outline?.let { o -> NextStep.of(o).takeIf { it !is NextStep.CourseComplete }?.let { ContinueCard(o.subject, it) } }
        }
    }

    val state: StateFlow<SubjectsUiState> = combine(
        subjects.observeSubjects(),
        continueCard,
        learning.observeReviewsDueCount(),
    ) { list, card, due -> SubjectsUiState.Ready(list, card, due) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectsUiState.Loading)
}
