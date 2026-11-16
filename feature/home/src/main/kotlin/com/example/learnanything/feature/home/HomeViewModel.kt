package com.example.learnanything.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.Subject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SubjectsUiState {
    data object Loading : SubjectsUiState
    data class Ready(val subjects: List<Subject>) : SubjectsUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(subjects: SubjectRepository) : ViewModel() {
    val state: StateFlow<SubjectsUiState> = subjects.observeSubjects()
        .map { SubjectsUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectsUiState.Loading)
}
