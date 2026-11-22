package com.example.learnanything.core.ai.openai

import com.example.learnanything.core.ai.AiChunk
import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.AiProvider
import com.example.learnanything.core.ai.AiRequest
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.ai.ProviderKind
import com.example.learnanything.core.ai.gemini.GeminiSchema
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Keys and model choices for OpenAI-compatible providers, by provider id. */
interface CompatibleSettings {
    suspend fun apiKey(providerId: String): String?
    suspend fun selectedModel(providerId: String): String?
}

/** A cloud service that speaks the OpenAI chat-completions API (Groq, OpenRouter, …). */
data class CompatibleSpec(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    /** OpenRouter: list only models that cost nothing. */
    val freeOnly: Boolean = false,
)

/**
 * One implementation for every OpenAI-compatible endpoint. Models come live from `/models`
 * (never hard-coded). Structured output asks for `json_schema` first, then falls back to
 * `json_object`, then to plain text, as models differ; every reply is still validated locally.
 */
class OpenAiCompatibleProvider(
    private val spec: CompatibleSpec,
    private val http: OkHttpClient,
    private val settings: CompatibleSettings,
    private val json: Json,
    baseUrl: HttpUrl = spec.baseUrl.toHttpUrl(),
) : AiProvider {
    override val id: String = spec.id
    override val displayName: String = spec.displayName
    override val kind = ProviderKind.CLOUD

    private val base = baseUrl
    @Volatile private var cachedModels: List<ModelInfo>? = null

    enum class Format { JSON_SCHEMA, JSON_OBJECT, NONE }

    @Volatile private var format = Format.JSON_SCHEMA

    override suspend fun isConfigured(): Boolean = !settings.apiKey(id).isNullOrBlank()

    override suspend fun listModels(): List<ModelInfo> {
        cachedModels?.let { return it }
        val key = settings.apiKey(id)?.takeIf { it.isNotBlank() } ?: throw AiException.NotConfigured(id)
        return listModelsWithKey(key).also { cachedModels = it }
    }

    /** Checks a key before it's saved; returns the usable chat models, best first. */
    suspend fun listModelsWithKey(key: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base.newBuilder().addPathSegment("models").build())
            .header("Authorization", "Bearer $key").get().build()
        val body = execute(request) { it.body.string() }
        val models = json.decodeFromString(ModelList.serializer(), body).data
            .filter { !spec.freeOnly || it.isFree() }
            .map { ModelInfo(it.id, it.name ?: it.id, outputTokenLimit = it.contextLength) }
        CompatibleModelRanker.rank(models)
    }

    fun clearModelCache() { cachedModels = null }

    override fun generate(request: AiRequest): Flow<AiChunk> = flow {
        val key = settings.apiKey(id)?.takeIf { it.isNotBlank() } ?: throw AiException.NotConfigured(id)
        val model = request.model ?: settings.selectedModel(id) ?: listModels().firstOrNull()?.id
            ?: throw AiException.Server(id, 404, "No free chat model is available for this key")
        var finish: String? = null
        while (true) {
            val attempt = format
            val call = Request.Builder()
                .url(base.newBuilder().addPathSegments("chat/completions").build())
                .header("Authorization", "Bearer $key")
                .header("X-Title", "Learn Anything")
                .post(body(request, model, attempt).toString().toRequestBody(JSON_MEDIA))
                .build()
            val response = try {
                http.newCall(call).execute()
            } catch (e: IOException) {
                throw AiException.Network(id, e)
            }
            try {
                if (!response.isSuccessful) {
                    val text = response.body.string()
                    if (response.code == 400 && attempt != Format.NONE && rejectsFormat(text)) {
                        format = if (attempt == Format.JSON_SCHEMA) Format.JSON_OBJECT else Format.NONE
                        continue
                    }
                    throw errorFor(response.code, text, response.header("retry-after"))
                }
                val source = response.body.source()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = try { source.readUtf8Line() } catch (e: IOException) { throw AiException.Network(id, e) } ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue
                    if (payload == "[DONE]") break
                    val chunk = runCatching { json.decodeFromString(StreamChunk.serializer(), payload) }.getOrNull() ?: continue
                    chunk.error?.let { throw AiException.Server(id, it.code ?: 500, it.message ?: "stream error") }
                    val choice = chunk.choices.firstOrNull() ?: continue
                    choice.delta?.content?.takeIf { it.isNotEmpty() }?.let { emit(AiChunk.Text(it)) }
                    choice.finishReason?.let { finish = it }
                }
            } finally {
                response.close()
            }
            break
        }
        if (finish == "content_filter") throw AiException.Blocked(id, "content_filter")
        emit(AiChunk.Done(model, finish))
    }.flowOn(Dispatchers.IO)

    internal fun body(request: AiRequest, model: String, format: Format): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", true)
        request.temperature?.let { put("temperature", it) }
        val system = request.schemaJson?.let {
            request.system + "\n\nReply with one JSON object only (no prose, no code fences) that satisfies this JSON Schema, " +
                "including every length, count and range limit:\n" + it
        } ?: request.system
        putJsonArray("messages") {
            addJsonObject { put("role", "system"); put("content", system) }
            addJsonObject { put("role", "user"); put("content", request.prompt) }
        }
        val schema: JsonElement? = request.schemaJson?.let { GeminiSchema.simplify(json.parseToJsonElement(it)) }
        if (schema != null) {
            when (format) {
                Format.JSON_SCHEMA -> putJsonObject("response_format") {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", "reply")
                        put("strict", false)
                        put("schema", schema)
                    }
                }
                Format.JSON_OBJECT -> putJsonObject("response_format") { put("type", "json_object") }
                Format.NONE -> Unit
            }
        }
    }

    private fun rejectsFormat(body: String): Boolean {
        val b = body.lowercase()
        return "response_format" in b || "json_schema" in b || "json mode" in b || "structured output" in b
    }

    internal fun errorFor(code: Int, body: String, retryAfter: String?): AiException {
        val message = runCatching { json.decodeFromString(ErrorEnvelope.serializer(), body).error?.message }.getOrNull()
            ?: body.take(200).ifBlank { "HTTP $code" }
        return when (code) {
            401, 403 -> AiException.Unauthorized(id, message)
            // 402: OpenRouter's free models need a small credit balance on some accounts; treat as a limit.
            402, 429 -> AiException.RateLimited(id, retryAfter?.toDoubleOrNull()?.let { kotlin.math.ceil(it).toLong() }, message)
            else -> AiException.Server(id, code, message)
        }
    }

    private fun <T> execute(request: Request, read: (okhttp3.Response) -> T): T {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw AiException.Network(id, e)
        }
        return response.use {
            if (!it.isSuccessful) throw errorFor(it.code, it.body.string(), it.header("retry-after"))
            read(it)
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        val GROQ = CompatibleSpec("groq", "Groq", "https://api.groq.com/openai/v1/")
        val OPENROUTER = CompatibleSpec("openrouter", "OpenRouter", "https://openrouter.ai/api/v1/", freeOnly = true)
    }
}

/** Orders chat models by traits: drops non-chat models, then prefers bigger ones (parameter count in the id). */
object CompatibleModelRanker {
    private val NON_CHAT = listOf("whisper", "tts", "guard", "embed", "moderation", "orpheus", "playai", "audio", "image", "vision-only")
    private val SIZE = Regex("""(\d+(?:\.\d+)?)b\b""")

    fun isChat(m: ModelInfo) = NON_CHAT.none { it in m.id.lowercase() }

    /** Parameters in billions, from names like "llama-3.3-70b" or "gpt-oss-120b"; 0 when unknown. */
    fun size(m: ModelInfo): Double = SIZE.findAll(m.id.lowercase()).mapNotNull { it.groupValues[1].toDoubleOrNull() }.maxOrNull() ?: 0.0

    fun rank(models: List<ModelInfo>): List<ModelInfo> =
        models.filter(::isChat).sortedWith(
            compareByDescending<ModelInfo> { size(it) }.thenByDescending { it.outputTokenLimit ?: 0 }.thenBy { it.id },
        )
}

@Serializable private data class ModelList(val data: List<ModelDto> = emptyList())

@Serializable private data class ModelDto(
    val id: String,
    val name: String? = null,
    @kotlinx.serialization.SerialName("context_length") val contextLength: Int? = null,
    val pricing: Pricing? = null,
) {
    fun isFree(): Boolean = id.endsWith(":free") ||
        (pricing != null && pricing.prompt?.toDoubleOrNull() == 0.0 && pricing.completion?.toDoubleOrNull() == 0.0)
}

@Serializable private data class Pricing(val prompt: String? = null, val completion: String? = null)

@Serializable private data class StreamChunk(val choices: List<Choice> = emptyList(), val error: StreamError? = null)
@Serializable private data class Choice(val delta: Delta? = null, @kotlinx.serialization.SerialName("finish_reason") val finishReason: String? = null)
@Serializable private data class Delta(val content: String? = null)
@Serializable private data class StreamError(val code: Int? = null, val message: String? = null)
@Serializable private data class ErrorEnvelope(val error: ErrorBody? = null)
@Serializable private data class ErrorBody(val message: String? = null)
