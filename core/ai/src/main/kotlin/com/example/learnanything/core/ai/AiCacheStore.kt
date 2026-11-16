package com.example.learnanything.core.ai

import com.example.learnanything.core.model.ai.CallType

/** Response cache keyed by prompt hash. Implemented on Room in core/data; never backed up. */
interface AiCacheStore {
    suspend fun get(promptHash: String): String?
    suspend fun put(promptHash: String, callType: CallType, response: String)
}
