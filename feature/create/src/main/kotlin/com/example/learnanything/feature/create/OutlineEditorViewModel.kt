package com.example.learnanything.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.data.usecase.StartCourseUseCase
import com.example.learnanything.core.model.SubjectOutline
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = OutlineEditorViewModel.Factory::class)
class OutlineEditorViewModel @AssistedInject constructor(
    @Assisted private val subjectId: String,
    private val subjects: SubjectRepository,
    private val startCourse: StartCourseUseCase,
) : ViewModel() {

    val outline: StateFlow<SubjectOutline?> =
        subjects.observeOutline(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    fun renameChapter(id: String, title: String) = launch { subjects.renameChapter(id, title) }
    fun renameTopic(id: String, title: String) = launch { subjects.renameTopic(id, title) }
    fun moveChapter(id: String, up: Boolean) = launch { subjects.moveChapter(id, up) }
    fun moveTopic(id: String, up: Boolean) = launch { subjects.moveTopic(id, up) }
    fun deleteChapter(id: String) = launch { subjects.deleteChapter(id) }
    fun deleteTopic(id: String) = launch { subjects.deleteTopic(id) }
    fun addTopic(chapterId: String, title: String) = launch { subjects.addTopic(chapterId, title) }

    fun start() = launch { if (startCourse(subjectId)) _started.value = true }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    @AssistedFactory
    interface Factory {
        fun create(subjectId: String): OutlineEditorViewModel
    }
}
