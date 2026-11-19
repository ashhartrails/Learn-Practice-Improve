package com.example.learnanything.core.model.practical

import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.ai.PromptSpec
import kotlinx.serialization.Serializable

@Serializable
data class GradeResponse(val criteria: List<CriterionGrade>, val summary: String)

@Serializable
data class CriterionGrade(
    val index: Int,
    val met: Boolean,
    /** Exact quote from the learner's answer that shows the criterion is met. Empty when not met. */
    val evidence: String,
    val feedback: String,
)

data class RubricScore(val score: Double, val passed: Boolean, val earned: Int, val total: Int)

object GradeRules {
    /** Code checks the grader: every criterion graded once, and every quote really is in the answer. */
    fun check(g: GradeResponse, rubric: List<RubricCriterion>, answer: String): List<String> {
        val e = mutableListOf<String>()
        val indexes = g.criteria.map { it.index }
        if (indexes.sorted() != rubric.indices.toList()) e += "grade every criterion exactly once, with index 0 to ${rubric.size - 1}"
        val text = squash(answer)
        g.criteria.forEachIndexed { i, c ->
            if (c.met) {
                if (c.evidence.isBlank()) e += "criteria[$i].evidence must quote the learner's answer when met is true"
                else if (!quoteFound(c.evidence, text)) e += "criteria[$i].evidence must be copied word for word from the learner's answer"
            }
        }
        return e
    }

    fun score(g: GradeResponse, rubric: List<RubricCriterion>): RubricScore {
        val total = rubric.sumOf { it.points }
        val earned = g.criteria.filter { it.met }.sumOf { rubric.getOrNull(it.index)?.points ?: 0 }
        val score = if (total == 0) 0.0 else earned.toDouble() / total
        return RubricScore(score, score >= PracticalRules.WRITTEN_PASS_MARK, earned, total)
    }

    /** Lets the model shorten a quote with "..." as long as every piece is in the answer. */
    private fun quoteFound(quote: String, squashedAnswer: String): Boolean =
        quote.split("...", "…").map { squash(it.trim('"', '\'', '“', '”', ' ')) }.filter { it.isNotEmpty() }
            .let { parts -> parts.isNotEmpty() && parts.all { it in squashedAnswer } }

    private fun squash(s: String) = s.lowercase().replace(Regex("\\s+"), " ").trim()
}

object GradePrompt {
    const val VERSION = 1

    private val SYSTEM = """
        You grade a learner's written answer against a rubric, strictly but fairly.
        For each criterion give its index, whether it is met, and feedback in one or two
        sentences. When a criterion is met, "evidence" must be a short exact quote copied word
        for word from the learner's answer that shows it. If you can't quote it, it is not met.
        When not met, leave evidence empty and say what was missing. "summary" is two sentences
        of encouragement and the single most useful next step. Never include URLs.
    """.trimIndent()

    fun build(practical: PracticalResponse, answer: String): PromptSpec = PromptSpec(
        callType = CallType.GRADE,
        version = VERSION,
        system = SYSTEM,
        user = buildString {
            appendLine("Task: ${practical.task}")
            appendLine("Rubric:")
            practical.rubric.forEachIndexed { i, c -> appendLine("$i. ${c.criterion} (${c.points} points)") }
            appendLine("Model answer (for your reference only): ${practical.modelAnswer}")
            appendLine("Learner's answer:")
            appendLine("<<<")
            appendLine(answer)
            append(">>>")
        },
        schema = AiSchema.GRADE_V1,
    )
}
