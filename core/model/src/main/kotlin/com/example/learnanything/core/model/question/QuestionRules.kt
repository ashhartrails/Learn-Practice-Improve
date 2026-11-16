package com.example.learnanything.core.model.question

import com.example.learnanything.core.model.TextSafety

/** Checks that make a question gradeable by code. The AI's answer key must be consistent. */
object QuestionRules {
    fun check(q: QuestionDto, path: String): List<String> {
        val p = mutableListOf<String>()
        if (q.prompt.isBlank()) p += "$path.prompt is blank"
        when (q.kind) {
            QuestionKind.SINGLE, QuestionKind.MULTI -> {
                if (q.options.size !in 2..6) p += "$path needs 2 to 6 options"
                val normalized = q.options.map { it.trim().lowercase() }
                if (normalized.toSet().size != normalized.size) p += "$path has duplicate options"
                if (q.options.any { it.isBlank() }) p += "$path has a blank option"
                if (q.correct.isEmpty()) p += "$path.correct is empty"
                if (q.correct.any { it !in q.options.indices }) p += "$path.correct has an index outside the options"
                if (q.correct.toSet().size != q.correct.size) p += "$path.correct has duplicates"
                if (q.kind == QuestionKind.SINGLE && q.correct.size != 1) {
                    p += "$path is single_choice so correct must have exactly one index"
                }
                if (q.kind == QuestionKind.MULTI && q.correct.size == q.options.size) {
                    p += "$path marks every option correct"
                }
            }
            QuestionKind.NUMERIC -> {
                val n = q.number
                if (n == null || n.isNaN() || n.isInfinite()) p += "$path is numeric but number is missing"
                val t = q.tolerance
                if (t != null && (t < 0 || t.isNaN())) p += "$path.tolerance must be zero or more"
                if (q.options.isNotEmpty()) p += "$path is numeric so options must be empty"
            }
            QuestionKind.SHORT_TEXT -> {
                if (q.accepted.isEmpty() || q.accepted.any { it.isBlank() }) {
                    p += "$path is short_text so accepted needs at least one non-blank answer"
                }
                if (q.accepted.any { it.trim().split(Regex("\\s+")).size > 4 }) {
                    p += "$path accepted answers must be short (at most 4 words) so they can be matched exactly"
                }
            }
            else -> p += "$path.kind \"${q.kind}\" is not supported"
        }
        p += TextSafety.urlProblems(
            listOf("$path.prompt" to q.prompt, "$path.explanation" to q.explanation) +
                q.options.mapIndexed { i, o -> "$path.options[$i]" to o },
        )
        return p
    }
}
