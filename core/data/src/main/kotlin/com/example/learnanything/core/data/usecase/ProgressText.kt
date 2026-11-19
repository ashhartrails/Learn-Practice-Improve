package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.CallProgress

/** Readable progress for a running AI call, e.g. "Writing your lesson… 1200 characters". */
object ProgressText {
    fun of(p: CallProgress, what: String): String {
        val retry = if (p.attempt > 1) " (fixing a problem and retrying)" else ""
        return if (p.charsReceived == 0) "Asking ${p.providerName}$retry…"
        else "Writing $what$retry… ${p.charsReceived} characters"
    }
}
