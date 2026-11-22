package com.example.learnanything.core.ai.gemini

/** What the Gemini provider needs from settings. Implemented in core/data. */
interface GeminiSettings {
    suspend fun apiKey(): String?
    /** The learner's chosen model id, or null to let the app pick. */
    suspend fun selectedModel(): String?
}
