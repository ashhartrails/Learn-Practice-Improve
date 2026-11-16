package com.example.learnanything.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.ProgressSummary
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.SubjectStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ProgressUiState(val summary: ProgressSummary, val subjects: List<Subject>)

@HiltViewModel
class ProgressViewModel @Inject constructor(learning: LearningRepository, subjects: SubjectRepository) : ViewModel() {
    val state: StateFlow<ProgressUiState?> = combine(learning.observeProgress(), subjects.observeSubjects()) { p, s ->
        ProgressUiState(p, s.filter { it.status == SubjectStatus.ACTIVE })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
