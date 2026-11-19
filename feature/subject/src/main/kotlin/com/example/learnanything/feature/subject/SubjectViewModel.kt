package com.example.learnanything.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.NextStep
import com.example.learnanything.core.model.SubjectOutline
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SubjectUiState {
    data object Loading : SubjectUiState
    data object NotFound : SubjectUiState
    data class Ready(val outline: SubjectOutline, val next: NextStep) : SubjectUiState
}

@HiltViewModel(assistedFactory = SubjectViewModel.Factory::class)
class SubjectViewModel @AssistedInject constructor(
    @Assisted private val subjectId: String,
    private val subjects: SubjectRepository,
) : ViewModel() {

    val state: StateFlow<SubjectUiState> = subjects.observeOutline(subjectId)
        .map { if (it == null) SubjectUiState.NotFound else SubjectUiState.Ready(it, NextStep.of(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectUiState.Loading)

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            subjects.delete(subjectId)
            onDone()
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(subjectId: String): SubjectViewModel
    }
}
