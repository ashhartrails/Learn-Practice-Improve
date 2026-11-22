package com.example.learnanything.core.model.outline

import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.ai.PromptSpec
import com.example.learnanything.core.model.clarify.Clarification

/** Versioned prompt for the `outline` call. Bump [VERSION] whenever the text changes. */
object OutlinePrompt {
    const val VERSION = 2

    private val SYSTEM = """
        You are an expert curriculum designer building a self-paced course for one learner.
        Structure: the course has chapters; each chapter has topics. Each topic is a short,
        interactive lesson of 5 to 30 minutes. Each chapter ends with a quiz and a practical,
        so chapter goals must be concrete and testable.
        Rules:
        - Order chapters and topics so that each builds only on earlier ones.
        - Topic titles must be unique across the whole course.
        - Objectives are short, observable outcomes ("Write an INNER JOIN across two tables").
        - Prerequisites name concepts, never links. Never include URLs anywhere.
        - Size the course to the learner's weekly time: roughly 4 to 8 weeks of work.
        - Reply with JSON only, matching the provided schema. No markdown, no commentary.
    """.trimIndent()

    fun build(request: OutlineRequest, clarifications: List<Clarification> = emptyList()): PromptSpec = PromptSpec(
        callType = CallType.OUTLINE,
        version = VERSION,
        system = SYSTEM,
        user = """
            Learner goal: ${request.goal.trim()}
            Current level: ${request.level.label}
            Time available: ${request.weeklyHours} hours per week
        """.trimIndent() + clarifications.joinToString("") { "\nLearner said — ${it.question}: ${it.answer}" } +
            "\nBuild the course outline.",
        schema = AiSchema.OUTLINE_V1,
    )
}
