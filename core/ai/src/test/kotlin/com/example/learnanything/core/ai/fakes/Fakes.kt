package com.example.learnanything.core.ai.fakes

import com.example.learnanything.core.ai.AiCacheStore
import com.example.learnanything.core.ai.AiChunk
import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.AiProvider
import com.example.learnanything.core.ai.AiRequest
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.ai.ProviderKind
import com.example.learnanything.core.model.ai.CallType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Replies with scripted outputs in order. A Throwable entry is thrown instead. */
class ScriptedProvider(
    override val id: String,
    private vararg val script: Any,
    private val configured: Boolean = true,
) : AiProvider {
    override val displayName = id
    override val kind = ProviderKind.CLOUD
    val requests = mutableListOf<AiRequest>()
    private var index = 0

    override suspend fun isConfigured() = configured
    override suspend fun listModels() = listOf(ModelInfo("m", "m"))
    override fun generate(request: AiRequest): Flow<AiChunk> = flow {
        requests += request
        val next = script.getOrNull(index++) ?: error("$id: no scripted reply left")
        if (next is Throwable) throw next
        // Split into two chunks to exercise stream assembly.
        val text = next as String
        emit(AiChunk.Text(text.take(text.length / 2)))
        emit(AiChunk.Text(text.drop(text.length / 2)))
        emit(AiChunk.Done("m", "STOP"))
    }
}

class InMemoryCache : AiCacheStore {
    val entries = mutableMapOf<String, String>()
    override suspend fun get(promptHash: String) = entries[promptHash]
    override suspend fun put(promptHash: String, callType: CallType, response: String) {
        entries[promptHash] = response
    }
}

fun rateLimited(id: String) = AiException.RateLimited(id, 30, "quota")
