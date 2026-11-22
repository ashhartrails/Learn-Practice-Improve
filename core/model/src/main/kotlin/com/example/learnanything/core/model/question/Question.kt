package com.example.learnanything.core.model.question

import kotlinx.serialization.Serializable

/**
 * A question exactly as the AI returns it (flat, so it's easy for models to fill in).
 * Only [QuestionRules]-clean DTOs are converted to [Question] and shown.
 */
@Serializable
data class QuestionDto(
    val kind: String,
    val prompt: String,
    val options: List<String> = emptyList(),
    /** Zero-based indexes of the correct options (choice kinds). */
    val correct: List<Int> = emptyList(),
    val number: Double? = null,
    val tolerance: Double? = null,
    val unit: String? = null,
    /** Accepted answers (short_text). */
    val accepted: List<String> = emptyList(),
    val explanation: String,
)

object QuestionKind {
    const val SINGLE = "single_choice"
    const val MULTI = "multi_choice"
    const val NUMERIC = "numeric"
    const val SHORT_TEXT = "short_text"
}

/** A validated, gradeable question. */
sealed interface Question {
    val prompt: String
    val explanation: String

    data class SingleChoice(
        override val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
        override val explanation: String,
    ) : Question

    data class MultiChoice(
        override val prompt: String,
        val options: List<String>,
        val correct: Set<Int>,
        override val explanation: String,
    ) : Question

    data class Numeric(
        override val prompt: String,
        val value: Double,
        val tolerance: Double,
        val unit: String?,
        override val explanation: String,
    ) : Question

    data class ShortText(
        override val prompt: String,
        val accepted: List<String>,
        override val explanation: String,
    ) : Question
}

/** Converts a DTO that passed [QuestionRules]. Throws if it didn't. */
fun QuestionDto.toQuestion(): Question = when (kind) {
    QuestionKind.SINGLE -> Question.SingleChoice(prompt, options, correct.single(), explanation)
    QuestionKind.MULTI -> Question.MultiChoice(prompt, options, correct.toSet(), explanation)
    QuestionKind.NUMERIC -> Question.Numeric(prompt, number!!, tolerance ?: 0.0, unit?.takeIf { it.isNotBlank() }, explanation)
    QuestionKind.SHORT_TEXT -> Question.ShortText(prompt, accepted, explanation)
    else -> error("Unknown question kind $kind")
}
