package com.example.learnanything.core.model.quiz

import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.ai.PromptSpec
import com.example.learnanything.core.model.question.QuestionDto
import com.example.learnanything.core.model.question.QuestionRules
import kotlinx.serialization.Serializable

@Serializable
data class QuizResponse(val questions: List<QuizQuestion>)

@Serializable
data class QuizQuestion(
    /** Zero-based index of the chapter topic this question tests. */
    val topicIndex: Int,
    val question: QuestionDto,
)

data class QuizTopic(val title: String, val objectives: List<String>)

data class QuizContext(
    val subjectTitle: String,
    val level: Level,
    val chapterTitle: String,
    val chapterGoal: String,
    val topics: List<QuizTopic>,
    /** Bumped for each retake so the cache doesn't return the same quiz. */
    val variant: Int,
)

object QuizRules {
    const val PASS_MARK = 0.8

    fun check(r: QuizResponse, topicCount: Int): List<String> {
        val p = mutableListOf<String>()
        r.questions.forEachIndexed { i, q ->
            if (q.topicIndex !in 0 until topicCount) p += "questions[$i].topicIndex must be between 0 and ${topicCount - 1}"
            p += QuestionRules.check(q.question, "questions[$i].question")
        }
        if (topicCount <= r.questions.size) {
            val missing = (0 until topicCount) - r.questions.map { it.topicIndex }.toSet()
            if (missing.isNotEmpty()) p += "every topic needs at least one question; missing topicIndex ${missing.joinToString()}"
        }
        val prompts = r.questions.map { it.question.prompt.trim().lowercase() }
        if (prompts.toSet().size != prompts.size) p += "questions must not repeat"
        return p
    }
}

object QuizPrompt {
    const val VERSION = 1

    private val SYSTEM = """
        You are writing an end-of-chapter quiz that decides whether the learner has met the
        chapter goal. Write 8 to 10 questions covering every topic, mixing recall with
        application to new situations. Questions must be gradeable by code:
        - single_choice: 3 or 4 options, exactly one index in "correct".
        - multi_choice: 4 or 5 options, two or more indexes in "correct".
        - numeric: answer in "number", absolute "tolerance", "unit" if any; options empty.
        - short_text: only for short exact answers; list every acceptable spelling in "accepted".
        - Always fill "explanation".
        Set topicIndex to the zero-based index of the topic each question tests.
        Use empty arrays for fields that don't apply. Never include URLs.
        Reply with JSON only, matching the schema.
    """.trimIndent()

    fun build(ctx: QuizContext): PromptSpec = PromptSpec(
        callType = CallType.QUIZ,
        version = VERSION,
        system = SYSTEM,
        user = buildString {
            appendLine("Course: ${ctx.subjectTitle} (learner level: ${ctx.level.label})")
            appendLine("Chapter: ${ctx.chapterTitle} (goal: ${ctx.chapterGoal})")
            appendLine("Topics:")
            ctx.topics.forEachIndexed { i, t -> appendLine("$i. ${t.title} — ${t.objectives.joinToString("; ")}") }
            append("Quiz version ${ctx.variant}. Write the quiz.")
        },
        schema = AiSchema.QUIZ_V1,
    )
}
