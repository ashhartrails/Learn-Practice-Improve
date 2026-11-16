package com.example.learnanything.feature.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.SubjectOutline
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SubjectUiState {
    data object Loading : SubjectUiState
    data object NotFound : SubjectUiState
    data class Ready(val outline: SubjectOutline) : SubjectUiState
}

@HiltViewModel(assistedFactory = SubjectViewModel.Factory::class)
class SubjectViewModel @AssistedInject constructor(
    @Assisted subjectId: String,
    subjects: SubjectRepository,
) : ViewModel() {

    val state: StateFlow<SubjectUiState> = subjects.observeOutline(subjectId)
        .map { if (it == null) SubjectUiState.NotFound else SubjectUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(subjectId: String): SubjectViewModel
    }
}
