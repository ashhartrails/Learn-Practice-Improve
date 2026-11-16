package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.FailureKind

/** A failure, worded for the learner. [needsSettings] shows an "Open Settings" action. */
data class UserMessage(val text: String, val needsSettings: Boolean = false)

object FailureMessages {
    fun of(kind: FailureKind, detail: String, retryAfterSeconds: Long?): UserMessage = when (kind) {
        FailureKind.NO_PROVIDER -> UserMessage("Add your free Gemini API key in Settings first.", needsSettings = true)
        FailureKind.UNAUTHORIZED -> UserMessage("Gemini rejected your API key. Check it in Settings.", needsSettings = true)
        FailureKind.RATE_LIMITED -> UserMessage(
            "You've hit Gemini's free-tier limit." +
                (retryAfterSeconds?.let { " Try again in about $it seconds." } ?: " Try again in a minute."),
        )
        FailureKind.NETWORK -> UserMessage(
            if (detail.contains("Timeout", ignoreCase = true)) {
                "Gemini took too long to answer. Try again."
            } else {
                "No connection. Check your internet and try again."
            } + "\nDetail: $detail",
        )
        FailureKind.BUSY -> UserMessage(
            "Gemini is busy right now. We retried twice. Try again in a minute, " +
                "or pick another model in Settings.",
            needsSettings = true,
        )
        FailureKind.BLOCKED -> UserMessage("Gemini declined this request. Try rewording it.")
        FailureKind.INVALID_OUTPUT -> UserMessage(
            "The AI's answer didn't pass our checks twice. Try again.\n" +
                "Detail: " + detail.substringAfter("validation: ").take(300),
        )
        FailureKind.PROVIDER_ERROR -> UserMessage(detail, needsSettings = detail.contains("404"))
    }
}
