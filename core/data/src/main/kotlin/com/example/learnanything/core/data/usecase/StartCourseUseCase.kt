package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.data.repository.SubjectRepository
import javax.inject.Inject

/** Ends outline editing. Refuses an empty course. */
class StartCourseUseCase @Inject constructor(private val subjects: SubjectRepository) {
    suspend operator fun invoke(subjectId: String): Boolean {
        val outline = subjects.outline(subjectId) ?: return false
        if (outline.topicCount == 0) return false
        subjects.activate(subjectId)
        return true
    }
}
