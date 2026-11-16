package com.example.learnanything.core.model.question

import kotlinx.serialization.Serializable

/** What the learner entered. Stored with each attempt. */
@Serializable
sealed interface Answer {
    @Serializable data class Choice(val selected: Set<Int>) : Answer
    @Serializable data class Number(val text: String) : Answer
    @Serializable data class Text(val text: String) : Answer
}

data class GradeResult(
    val correct: Boolean,
    /** 0.0 to 1.0. M1 grading is all-or-nothing. */
    val score: Double,
    /** The correct answer, readable. */
    val expected: String,
    val explanation: String,
)

/** Principle 1: code checks every answer it can. These results carry the "Exact" trust label. */
object AnswerChecker {
    fun check(question: Question, answer: Answer): GradeResult {
        val correct = when (question) {
            is Question.SingleChoice -> (answer as? Answer.Choice)?.selected == setOf(question.correctIndex)
            is Question.MultiChoice -> (answer as? Answer.Choice)?.selected == question.correct
            is Question.Numeric -> {
                val value = (answer as? Answer.Number)?.text?.let(::parseNumber)
                value != null && kotlin.math.abs(value - question.value) <= effectiveTolerance(question)
            }
            is Question.ShortText -> {
                val text = (answer as? Answer.Text)?.text?.let(::normalize)
                text != null && question.accepted.any { normalize(it) == text }
            }
        }
        return GradeResult(correct, if (correct) 1.0 else 0.0, expected(question), question.explanation)
    }

    fun expected(question: Question): String = when (question) {
        is Question.SingleChoice -> question.options[question.correctIndex]
        is Question.MultiChoice -> question.correct.sorted().joinToString(", ") { question.options[it] }
        is Question.Numeric -> buildString {
            append(formatNumber(question.value))
            question.unit?.let { append(' ').append(it) }
            if (question.tolerance > 0) append(" (±").append(formatNumber(question.tolerance)).append(')')
        }
        is Question.ShortText -> question.accepted.first()
    }

    /** Accepts "1,5", " 1.5 kg", "-3e2". Returns null when there's no number. */
    fun parseNumber(text: String): Double? {
        val match = Regex("""[-+]?\d+(?:[.,]\d+)?(?:[eE][-+]?\d+)?""").find(text.trim()) ?: return null
        return match.value.replace(',', '.').toDoubleOrNull()
    }

    fun normalize(text: String): String =
        text.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?', ';', ':').trim()

    private fun effectiveTolerance(q: Question.Numeric): Double =
        if (q.tolerance > 0) q.tolerance else 1e-9 * maxOf(1.0, kotlin.math.abs(q.value))

    private fun formatNumber(v: Double): String =
        if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
}
