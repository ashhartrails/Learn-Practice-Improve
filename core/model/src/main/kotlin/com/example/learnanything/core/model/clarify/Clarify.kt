package com.example.learnanything.core.model.clarify

import com.example.learnanything.core.model.TextSafety
import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.ai.PromptSpec
import com.example.learnanything.core.model.outline.OutlineRequest
import kotlinx.serialization.Serializable

@Serializable
data class ClarifyResponse(val questions: List<ClarifyQuestion>)

@Serializable
data class ClarifyQuestion(
    val id: String,
    val question: String,
    /** "choice" (pick one of [options]) or "text" (free answer). */
    val kind: String,
    val options: List<String> = emptyList(),
)

/** A clarifying question and the learner's answer, fed into the outline and lesson prompts. */
@Serializable
data class Clarification(val question: String, val answer: String)

object ClarifyRules {
    fun check(r: ClarifyResponse): List<String> {
        val p = mutableListOf<String>()
        val ids = r.questions.map { it.id.trim() }
        if (ids.toSet().size != ids.size) p += "question ids must be unique"
        r.questions.forEachIndexed { i, q ->
            if (q.kind == "choice") {
                if (q.options.size < 2) p += "questions[$i] is a choice question so it needs at least 2 options"
                if (q.options.map { it.trim().lowercase() }.toSet().size != q.options.size) {
                    p += "questions[$i] has duplicate options"
                }
            }
            p += TextSafety.urlProblems(listOf("questions[$i].question" to q.question))
        }
        return p
    }
}

object ClarifyPrompt {
    const val VERSION = 1

    private val SYSTEM = """
        You are a tutor preparing a personal course. Before designing it, ask the learner
        2 or 3 short questions whose answers would change the course the most: for example
        why they want to learn this, what they already know, or which tools or context they use.
        Prefer "choice" questions with 3 to 5 options; use "text" only when options can't cover it.
        Never include URLs. Reply with JSON only, matching the provided schema.
    """.trimIndent()

    fun build(request: OutlineRequest): PromptSpec = PromptSpec(
        callType = CallType.CLARIFY,
        version = VERSION,
        system = SYSTEM,
        user = """
            Learner goal: ${request.goal.trim()}
            Current level: ${request.level.label}
            Time available: ${request.weeklyHours} hours per week
        """.trimIndent(),
        schema = AiSchema.CLARIFY_V1,
    )
}
