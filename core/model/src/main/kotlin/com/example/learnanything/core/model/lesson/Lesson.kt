package com.example.learnanything.core.model.lesson

import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.TextSafety
import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.ai.PromptSpec
import com.example.learnanything.core.model.clarify.Clarification
import com.example.learnanything.core.model.question.QuestionDto
import com.example.learnanything.core.model.question.QuestionRules
import kotlinx.serialization.Serializable

@Serializable
data class LessonResponse(
    val whyItMatters: String,
    val cards: List<LessonCard>,
    val workedExample: WorkedExample,
    val tryIt: QuestionDto,
    val checks: List<QuestionDto>,
)

@Serializable
data class LessonCard(val heading: String, val body: String)

@Serializable
data class WorkedExample(val problem: String, val steps: List<String>, val answer: String)

data class LessonContext(
    val subjectTitle: String,
    val subjectGoal: String,
    val level: Level,
    val clarifications: List<Clarification>,
    val chapterTitle: String,
    val chapterGoal: String,
    val topicTitle: String,
    val objectives: List<String>,
    val prerequisites: List<String>,
    val earlierTopics: List<String>,
)

object LessonRules {
    fun check(l: LessonResponse): List<String> {
        val p = mutableListOf<String>()
        p += QuestionRules.check(l.tryIt, "tryIt")
        l.checks.forEachIndexed { i, q -> p += QuestionRules.check(q, "checks[$i]") }
        val prompts = l.checks.map { it.prompt.trim().lowercase() }
        if (prompts.toSet().size != prompts.size) p += "check questions must be different from each other"
        p += TextSafety.urlProblems(
            listOf("whyItMatters" to l.whyItMatters, "workedExample.problem" to l.workedExample.problem,
                "workedExample.answer" to l.workedExample.answer) +
                l.cards.flatMapIndexed { i, c -> listOf("cards[$i].heading" to c.heading, "cards[$i].body" to c.body) } +
                l.workedExample.steps.mapIndexed { i, s -> "workedExample.steps[$i]" to s },
        )
        return p
    }
}

object LessonPrompt {
    const val VERSION = 1

    private val SYSTEM = """
        You are an expert tutor writing one short, interactive lesson for a single learner.
        Write for the learner's level. Be concrete and use examples from the learner's context.
        Structure:
        - whyItMatters: 1 or 2 sentences on why this topic matters to this learner.
        - cards: 3 to 6 explanation cards, one idea each, 40 to 120 words per body.
        - workedExample: a realistic problem solved step by step.
        - tryIt: one practice question about the worked example's idea.
        - checks: 2 or 3 questions that test the objectives. Make them different from tryIt.
        Card bodies may use only this formatting: **bold**, `inline code`, fenced code blocks
        with ``` on their own lines, and lists with lines starting "- ". No headings, tables,
        links, images or HTML. Never include URLs or web addresses anywhere.
        Questions must be gradeable by code:
        - single_choice: 3 or 4 options, exactly one index in "correct".
        - multi_choice: 4 or 5 options, two or more indexes in "correct".
        - numeric: put the answer in "number", an absolute "tolerance", and "unit" if any; options empty.
        - short_text: only for one-word or short exact answers; list every acceptable spelling in "accepted".
        - Always fill "explanation" with why the answer is right.
        Use empty arrays for fields that don't apply. Reply with JSON only, matching the schema.
    """.trimIndent()

    fun build(ctx: LessonContext): PromptSpec = PromptSpec(
        callType = CallType.LESSON,
        version = VERSION,
        system = SYSTEM,
        user = buildString {
            appendLine("Course: ${ctx.subjectTitle}")
            appendLine("Learner goal: ${ctx.subjectGoal}")
            appendLine("Learner level: ${ctx.level.label}")
            ctx.clarifications.forEach { appendLine("Learner said — ${it.question}: ${it.answer}") }
            appendLine("Chapter: ${ctx.chapterTitle} (goal: ${ctx.chapterGoal})")
            appendLine("Topic: ${ctx.topicTitle}")
            appendLine("Objectives: ${ctx.objectives.joinToString("; ").ifEmpty { "explain the topic" }}")
            if (ctx.prerequisites.isNotEmpty()) appendLine("Builds on: ${ctx.prerequisites.joinToString("; ")}")
            if (ctx.earlierTopics.isNotEmpty()) appendLine("Already covered: ${ctx.earlierTopics.joinToString("; ")}")
            append("Write the lesson.")
        },
        schema = AiSchema.LESSON_V1,
    )
}
