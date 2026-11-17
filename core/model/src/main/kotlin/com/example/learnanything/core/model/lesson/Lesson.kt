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
    const val MIN_CARDS = 5
    const val MIN_AVERAGE_WORDS = 60

    fun check(l: LessonResponse): List<String> {
        val p = mutableListOf<String>()
        p += QuestionRules.check(l.tryIt, "tryIt")
        l.checks.forEachIndexed { i, q -> p += QuestionRules.check(q, "checks[$i]") }
        if (l.cards.size < MIN_CARDS) p += "write at least $MIN_CARDS cards (you wrote ${l.cards.size})"
        val words = l.cards.map { c -> c.body.split(Regex("\\s+")).count { it.isNotBlank() } }
        if (words.isNotEmpty() && words.average() < MIN_AVERAGE_WORDS) {
            p += "cards are too short: aim for 80 to 180 words per card body (average was ${words.average().toInt()})"
        }
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
    /** v2 (M3): longer, deeper lessons after owner feedback that v1 lessons were too short. */
    const val VERSION = 2

    private val SYSTEM = """
        You are an expert tutor writing one short, interactive lesson for a single learner.
        Write for the learner's level. Be concrete and use examples from the learner's context.
        Teach the topic properly: the learner should be able to apply it afterwards, not just
        recognise the words. Build up from what they already know, explain the reasoning behind
        each idea, and show it in action.
        Structure:
        - whyItMatters: 2 or 3 sentences on why this topic matters to this learner.
        - cards: 5 to 8 explanation cards, one idea each, 80 to 180 words per body. Include a
          short concrete example in most cards (a code snippet, a calculation, a real case).
          The second-to-last card is "Common mistakes": 2 to 4 typical errors and how to avoid them.
          The last card is "Recap": the key points as a "- " list.
        - workedExample: a realistic problem solved in 3 to 6 clear steps, explaining why each step.
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
