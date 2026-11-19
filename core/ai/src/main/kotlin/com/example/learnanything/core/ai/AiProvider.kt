package com.example.learnanything.core.ai

import com.example.learnanything.core.model.ai.CallType
import kotlinx.coroutines.flow.Flow

/** A source of model output: a cloud API, or (from M5) an on-device engine. */
interface AiProvider {
    /** Stable id, e.g. `gemini`. Stored in settings and cache metadata. */
    val id: String
    val displayName: String
    val kind: ProviderKind

    /** True when the provider has what it needs (a key, a downloaded model) to take calls. */
    suspend fun isConfigured(): Boolean

    /** Models available right now. Never hard-code model names: always ask the provider. */
    suspend fun listModels(): List<ModelInfo>

    /** Streams the response text. Throws [AiException] on failure. */
    fun generate(request: AiRequest): Flow<AiChunk>
}

enum class ProviderKind { CLOUD, LOCAL }

data class ModelInfo(
    val id: String,
    val displayName: String,
    val description: String = "",
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val isPreview: Boolean = false,
)

data class AiRequest(
    val callType: CallType,
    val system: String,
    val prompt: String,
    /** JSON schema the response must follow, as a JSON string. Null for free text. */
    val schemaJson: String?,
    /** Explicit model id. Null lets the provider use the learner's choice or its best pick. */
    val model: String? = null,
    val temperature: Double? = null,
)

sealed interface AiChunk {
    data class Text(val text: String) : AiChunk
    data class Done(val model: String, val finishReason: String?) : AiChunk
}

/** Everything that can go wrong in a provider call. Rate limits are normal, not crashes. */
sealed class AiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    abstract val providerId: String

    class NotConfigured(override val providerId: String) :
        AiException("$providerId is not set up")

    class Unauthorized(override val providerId: String, detail: String) :
        AiException("$providerId rejected the API key: $detail")

    class RateLimited(override val providerId: String, val retryAfterSeconds: Long?, detail: String) :
        AiException("$providerId rate limit reached: $detail")

    class Blocked(override val providerId: String, val reason: String) :
        AiException("$providerId blocked the request ($reason)")

    class Network(override val providerId: String, cause: Throwable) :
        AiException("Could not reach $providerId (${cause::class.simpleName}: ${cause.message})", cause)

    class Server(override val providerId: String, val httpCode: Int, detail: String) :
        AiException("$providerId error $httpCode: $detail")

    class BadResponse(override val providerId: String, detail: String) :
        AiException("$providerId sent an unreadable response: $detail")
}
