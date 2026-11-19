package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.CallProgress
import com.example.learnanything.core.ai.FailureKind
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.StructuredResult
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.clarify.Clarification
import com.example.learnanything.core.model.outline.OutlinePrompt
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineRules
import javax.inject.Inject

sealed interface OutlineResult {
    data class Saved(val subjectId: String) : OutlineResult
    data class Failed(val kind: FailureKind, val message: String, val retryAfterSeconds: Long?) : OutlineResult
}

/** Builds an outline with the AI, validates it, and saves it to Room as a draft for editing. */
class GenerateOutlineUseCase @Inject constructor(
    private val caller: StructuredCaller,
    private val subjects: SubjectRepository,
) {
    suspend operator fun invoke(
        request: OutlineRequest,
        clarifications: List<Clarification> = emptyList(),
        onProgress: (CallProgress) -> Unit = {},
    ): OutlineResult {
        val spec = OutlinePrompt.build(request, clarifications)
        return when (val result = caller.call(spec, OutlineResponse.serializer(), OutlineRules::check, onProgress)) {
            is StructuredResult.Success ->
                OutlineResult.Saved(subjects.saveOutline(request, result.value, clarifications, draft = true))
            is StructuredResult.Failure -> OutlineResult.Failed(result.kind, result.message, result.retryAfterSeconds)
        }
    }
}
