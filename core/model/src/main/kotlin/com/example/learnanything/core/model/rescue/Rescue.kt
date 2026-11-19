package com.example.learnanything.core.model.rescue

import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.TextSafety
import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.ai.PromptSpec
import com.example.learnanything.core.model.practical.RubricCriterion
import com.example.learnanything.core.model.question.AnswerChecker
import com.example.learnanything.core.model.question.QuestionDto
import com.example.learnanything.core.model.question.QuestionRules
import kotlinx.serialization.Serializable

/** The "I don't get it" ladder, in its default order. */
enum class RescueRung(val label: String, val usesAi: Boolean) {
    SIMPLER("Simpler words", true),
    ANALOGY("An analogy", true),
    BLANKS("Fill in the blanks", true),
    VIDEO("A short video", false),
    SIMULATION("Try it in a simulation", false),
    SOCRATIC("Step-by-step questions", true),
    TEACH_BACK("Teach it back", true),
    PREREQUISITE("Check the basics", true),
}

object RescueLadder {
    /**
     * Order for this learner: the rung that helped most often goes first (ties keep the
     * default order), then the rest in the default order.
     */
    fun order(helpedCounts: Map<RescueRung, Int>): List<RescueRung> {
        val best = helpedCounts.filterValues { it > 0 }.entries
            .sortedWith(compareByDescending<Map.Entry<RescueRung, Int>> { it.value }.thenBy { it.key.ordinal })
            .firstOrNull()?.key ?: return RescueRung.entries
        return listOf(best) + RescueRung.entries.filter { it != best }
    }

    fun next(order: List<RescueRung>, current: RescueRung): RescueRung? =
        order.getOrNull(order.indexOf(current) + 1)
}

@Serializable
data class Blank(val accepted: List<String>)

@Serializable
data class Prerequisite(val concept: String, val explanation: String, val question: QuestionDto)

/** One AI-written rung. Fields that don't apply to the rung are empty. */
@Serializable
data class RescueResponse(
    val title: String,
    val body: String,
    /** BLANKS: answers for the markers [1], [2], … in [body]. */
    val blanks: List<Blank> = emptyList(),
    /** SOCRATIC: questions that lead to the idea one step at a time. */
    val questions: List<QuestionDto> = emptyList(),
    /** PREREQUISITE. */
    val prerequisites: List<Prerequisite> = emptyList(),
    /** TEACH_BACK: what a good explanation includes. */
    val rubric: List<RubricCriterion> = emptyList(),
)

data class RescueContext(
    val subjectTitle: String,
    val level: Level,
    val topicTitle: String,
    val objectives: List<String>,
    val prerequisites: List<String>,
    /** The lesson's card text, so the rescue explains it differently. */
    val lessonSummary: String,
    /** From the learner profile; may be blank. */
    val background: String,
    val knownSkills: String,
)

object RescueRules {
    private val MARKER = Regex("""\[(\d+)]""")

    fun check(r: RescueResponse, rung: RescueRung): List<String> {
        val e = mutableListOf<String>()
        val words = r.body.split(Regex("\\s+")).count { it.isNotBlank() }
        when (rung) {
            RescueRung.SIMPLER, RescueRung.ANALOGY -> if (words < 40) e += "body needs at least 40 words"
            RescueRung.BLANKS -> {
                if (r.blanks.size !in 1..5) e += "give 1 to 5 blanks"
                val markers = MARKER.findAll(r.body).map { it.groupValues[1].toInt() }.toList()
                if (markers != (1..r.blanks.size).toList()) {
                    e += "body must contain the markers [1] to [${r.blanks.size}] once each, in order"
                }
                r.blanks.forEachIndexed { i, b -> if (b.accepted.none { it.isNotBlank() }) e += "blanks[$i].accepted is empty" }
            }
            RescueRung.SOCRATIC -> {
                if (r.questions.size !in 2..4) e += "give 2 to 4 questions"
                r.questions.forEachIndexed { i, q -> e += QuestionRules.check(q, "questions[$i]") }
            }
            RescueRung.TEACH_BACK -> {
                if (r.rubric.size !in 3..4) e += "give 3 or 4 rubric criteria"
                r.rubric.forEachIndexed { i, c -> if (c.points !in 1..5) e += "rubric[$i].points must be 1 to 5" }
            }
            RescueRung.PREREQUISITE -> {
                if (r.prerequisites.size !in 1..3) e += "give 1 to 3 prerequisites"
                r.prerequisites.forEachIndexed { i, p -> e += QuestionRules.check(p.question, "prerequisites[$i].question") }
            }
            RescueRung.VIDEO, RescueRung.SIMULATION -> e += "rung ${rung.name} is not written by the AI"
        }
        e += TextSafety.urlProblems(
            listOf("title" to r.title, "body" to r.body) +
                r.prerequisites.mapIndexed { i, p -> "prerequisites[$i].explanation" to p.explanation },
        )
        return e
    }

    /** Code checks fill-in-the-blank answers. */
    fun checkBlanks(r: RescueResponse, answers: List<String>): List<Boolean> =
        r.blanks.mapIndexed { i, b ->
            val given = answers.getOrNull(i)?.let(AnswerChecker::normalize).orEmpty()
            given.isNotEmpty() && b.accepted.any { AnswerChecker.normalize(it) == given }
        }
}

object RescuePrompt {
    const val VERSION = 1

    private val SYSTEM = """
        A learner read a lesson and still doesn't get it. Help in exactly the way asked.
        Be warm, concrete and brief. Explain it differently from the lesson, not the same words.
        Use the learner's background when you have it. Text may use **bold**, `inline code`,
        fenced code blocks and "- " lists. Never include URLs or links.
        Use empty arrays for fields the requested help doesn't use. Questions must be
        gradeable by code (single_choice, multi_choice, numeric or short_text, with
        "explanation" always filled).
    """.trimIndent()

    private fun instruction(rung: RescueRung): String = when (rung) {
        RescueRung.SIMPLER -> "Explain the topic again in much simpler words, 80 to 200 words, short sentences, no jargon (or define each term)."
        RescueRung.ANALOGY -> "Explain the topic with one strong analogy from the learner's world (their job or interests if known), 80 to 200 words, then say where the analogy breaks down."
        RescueRung.BLANKS -> "Write a short worked example of the topic in \"body\" with 2 to 4 key steps replaced by the markers [1], [2], … . In \"blanks\", list each blank's acceptable answers (short exact words or numbers, every acceptable spelling)."
        RescueRung.SOCRATIC -> "Write a one-sentence intro in \"body\", then 3 or 4 questions in \"questions\" that lead the learner to the idea one small step at a time, easiest first."
        RescueRung.TEACH_BACK -> "In \"body\", ask the learner to explain the topic in their own words in 3 to 5 sentences, as if to a friend. In \"rubric\", give 3 or 4 criteria a good explanation meets (points 1 to 5)."
        RescueRung.PREREQUISITE -> "Find the 1 to 3 earlier ideas this topic depends on that the learner may be missing. For each, give a 2 to 4 sentence \"explanation\" and one check \"question\". Put a one-sentence intro in \"body\"."
        RescueRung.VIDEO, RescueRung.SIMULATION -> error("not an AI rung")
    }

    fun build(ctx: RescueContext, rung: RescueRung): PromptSpec = PromptSpec(
        callType = CallType.RESCUE,
        version = VERSION,
        system = SYSTEM,
        user = buildString {
            appendLine("Course: ${ctx.subjectTitle} (learner level: ${ctx.level.label})")
            appendLine("Topic: ${ctx.topicTitle}")
            appendLine("Objectives: ${ctx.objectives.joinToString("; ")}")
            if (ctx.prerequisites.isNotEmpty()) appendLine("Builds on: ${ctx.prerequisites.joinToString("; ")}")
            if (ctx.background.isNotBlank()) appendLine("Learner background: ${ctx.background}")
            if (ctx.knownSkills.isNotBlank()) appendLine("Learner already knows: ${ctx.knownSkills}")
            appendLine("The lesson said (in short): ${ctx.lessonSummary.take(1500)}")
            append("Help requested: ${instruction(rung)}")
        },
        schema = AiSchema.RESCUE_V1,
    )
}
