package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.CallProgress
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.StructuredResult
import com.example.learnanything.core.model.clarify.ClarifyPrompt
import com.example.learnanything.core.model.clarify.ClarifyQuestion
import com.example.learnanything.core.model.clarify.ClarifyResponse
import com.example.learnanything.core.model.clarify.ClarifyRules
import com.example.learnanything.core.model.outline.OutlineRequest
import javax.inject.Inject

sealed interface ClarifyResult {
    data class Questions(val questions: List<ClarifyQuestion>) : ClarifyResult
    data class Failed(val message: UserMessage) : ClarifyResult
}

class ClarifyGoalUseCase @Inject constructor(private val caller: StructuredCaller) {
    suspend operator fun invoke(request: OutlineRequest, onProgress: (CallProgress) -> Unit = {}): ClarifyResult =
        when (val r = caller.call(ClarifyPrompt.build(request), ClarifyResponse.serializer(), ClarifyRules::check, onProgress)) {
            is StructuredResult.Success -> ClarifyResult.Questions(r.value.questions)
            is StructuredResult.Failure -> ClarifyResult.Failed(FailureMessages.of(r.kind, r.message, r.retryAfterSeconds))
        }
}
