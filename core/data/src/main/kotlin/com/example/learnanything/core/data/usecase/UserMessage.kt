package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.FailureKind

/** A failure, worded for the learner. [needsSettings] shows an "Open Settings" action. */
data class UserMessage(val text: String, val needsSettings: Boolean = false)

object FailureMessages {
    private const val MORE = " Adding a free Groq or OpenRouter key in Settings gives the app somewhere else to try."

    fun of(kind: FailureKind, detail: String, retryAfterSeconds: Long?): UserMessage {
        val who = who(detail)
        return when (kind) {
            FailureKind.NO_PROVIDER -> UserMessage("Add a free AI key in Settings first (Gemini, Groq or OpenRouter).", needsSettings = true)
            FailureKind.UNAUTHORIZED -> UserMessage("$who rejected your API key. Check it in Settings.", needsSettings = true)
            FailureKind.RATE_LIMITED -> UserMessage(
                "You've hit $who's free-tier limit." +
                    (retryAfterSeconds?.let { " Try again in about $it seconds." } ?: " Try again in a minute.") + MORE,
            )
            FailureKind.NETWORK -> UserMessage(
                if (detail.contains("Timeout", ignoreCase = true)) {
                    "$who took too long to answer. Try again."
                } else {
                    "No connection. Check your internet and try again."
                } + "\nDetail: $detail",
            )
            FailureKind.BUSY -> UserMessage(
                "$who is busy right now. We retried twice. Try again in a minute, or pick another model in Settings." + MORE,
                needsSettings = true,
            )
            FailureKind.BLOCKED -> UserMessage("$who declined this request. Try rewording it.")
            FailureKind.INVALID_OUTPUT -> UserMessage(
                "The AI's answer didn't pass our checks twice. Try again.\n" +
                    "Detail: " + detail.substringAfter("validation: ").take(300),
            )
            FailureKind.PROVIDER_ERROR -> UserMessage(detail, needsSettings = detail.contains("404"))
        }
    }

    /** Provider errors start with the provider id ("groq rate limit reached: …"). */
    private fun who(detail: String): String {
        val first = detail.trim().substringBefore(' ').substringBefore(':').lowercase()
        return when (first) {
            "gemini" -> "Gemini"
            "groq" -> "Groq"
            "openrouter" -> "OpenRouter"
            else -> if ("Could not reach groq" in detail) "Groq" else if ("Could not reach openrouter" in detail) "OpenRouter" else "Gemini"
        }
    }
}
