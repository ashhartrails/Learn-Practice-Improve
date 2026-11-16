package com.example.learnanything.core.model.ai

/** A fully built prompt for one call. [version] is part of the cache key. */
data class PromptSpec(
    val callType: CallType,
    val version: Int,
    val system: String,
    val user: String,
    val schema: AiSchema,
)
