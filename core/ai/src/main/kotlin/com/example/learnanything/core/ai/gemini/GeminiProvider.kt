package com.example.learnanything.core.ai.gemini

import com.example.learnanything.core.ai.AiChunk
import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.AiProvider
import com.example.learnanything.core.ai.AiRequest
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.ai.ModelRanker
import com.example.learnanything.core.ai.ProviderKind
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Gemini API (Google AI Studio key) over REST with server-sent-event streaming.
 *
 * Structured output: the current REST docs put the schema at
 * `generationConfig.responseFormat.text.schema`; the SDKs still use `responseJsonSchema`.
 * We send the REST form first and, if the server rejects that field, switch to the other
 * form for the rest of the session. Either way the app validates the output itself.
 */
class GeminiProvider(
    private val http: OkHttpClient,
    private val settings: GeminiSettings,
    private val json: Json,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) : AiProvider {

    override val id = ID
    override val displayName = "Gemini"
    override val kind = ProviderKind.CLOUD

    internal enum class SchemaField { RESPONSE_FORMAT, RESPONSE_JSON_SCHEMA }

    @Volatile internal var schemaField = SchemaField.RESPONSE_FORMAT
        private set

    private val modelsLock = Mutex()
    private var cachedModels: Pair<String, List<ModelInfo>>? = null

    override suspend fun isConfigured(): Boolean = !settings.apiKey().isNullOrBlank()

    override suspend fun listModels(): List<ModelInfo> {
        val key = settings.apiKey()?.takeIf { it.isNotBlank() } ?: throw AiException.NotConfigured(id)
        return modelsLock.withLock {
            cachedModels?.takeIf { it.first == key }?.second
                ?: listModelsWithKey(key).also { cachedModels = key to it }
        }
    }

    /** Lists models for a key that is not saved yet. Used by Settings to test a key. */
    suspend fun listModelsWithKey(apiKey: String): List<ModelInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ModelInfo>()
        var pageToken: String? = null
        do {
            val url = baseUrl.newBuilder()
                .addPathSegments("v1beta/models")
                .addQueryParameter("pageSize", "1000")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val request = Request.Builder().url(url).header(KEY_HEADER, apiKey).get().build()
            val page = execute(request) { response ->
                decode(ModelsPageDto.serializer(), response.body.string())
            }
            page.models
                .filter { "generateContent" in it.supportedGenerationMethods }
                .mapTo(result) { it.toModelInfo() }
            pageToken = page.nextPageToken?.takeIf { it.isNotBlank() }
        } while (pageToken != null)
        result
    }

    fun invalidateModelCache() {
        cachedModels = null
    }

    override fun generate(request: AiRequest): Flow<AiChunk> = flow {
        val key = settings.apiKey()?.takeIf { it.isNotBlank() } ?: throw AiException.NotConfigured(id)
        val model = request.model ?: settings.selectedModel() ?: autoPickModel(request)
        val url = baseUrl.newBuilder()
            .addPathSegments("v1beta/models")
            .addPathSegment("$model:streamGenerateContent")
            .addQueryParameter("alt", "sse")
            .build()

        var finishReason: String? = null
        var switchedField = false
        var sendThoughts = true
        var sendSchema = true
        while (true) {
            val field = schemaField
            val httpRequest = Request.Builder()
                .url(url)
                .header(KEY_HEADER, key)
                .post(buildBody(request, field, sendThoughts, sendSchema).toString().toRequestBody(JSON_MEDIA))
                .build()
            val call = http.newCall(httpRequest)
            val response = try {
                call.execute()
            } catch (e: IOException) {
                throw AiException.Network(id, e)
            }
            try {
                if (!response.isSuccessful) {
                    val body = response.body.string()
                    if (sendSchema && response.code == 400 && GeminiSchema.isTooComplex(body)) {
                        sendSchema = false // ask for plain JSON; we still validate it locally
                        continue
                    }
                    if (sendThoughts && response.code == 400 && body.contains("thinking", ignoreCase = true)) {
                        sendThoughts = false // this model has no thinking settings
                        continue
                    }
                    if (!switchedField && response.code == 400 && rejectsSchemaField(body, field)) {
                        schemaField = field.other()
                        switchedField = true
                        continue
                    }
                    throw errorFor(response.code, body)
                }
                val source = response.body.source()
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = try {
                        source.readUtf8Line()
                    } catch (e: IOException) {
                        throw AiException.Network(id, e)
                    } ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val chunk = decode(StreamChunkDto.serializer(), payload)
                    chunk.promptFeedback?.blockReason?.let { throw AiException.Blocked(id, it) }
                    val candidate = chunk.candidates.firstOrNull() ?: continue
                    candidate.content?.parts.orEmpty()
                        .filter { it.thought != true }
                        .mapNotNull { it.text }
                        .forEach { emit(AiChunk.Text(it)) }
                    candidate.finishReason?.let { finishReason = it }
                }
            } finally {
                response.close()
            }
            break
        }
        if (finishReason in BLOCKING_FINISH_REASONS) throw AiException.Blocked(id, finishReason!!)
        emit(AiChunk.Done(model, finishReason))
    }.flowOn(Dispatchers.IO)

    private suspend fun autoPickModel(request: AiRequest): String =
        ModelRanker.pick(listModels(), request.callType.tier)?.id
            ?: throw AiException.Server(id, 404, "No text model is available for this key")

    internal fun buildBody(request: AiRequest, field: SchemaField, thoughts: Boolean = true, withSchema: Boolean = true): JsonObject = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", request.system) } }
        }
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") { addJsonObject { put("text", request.prompt) } }
            }
        }
        putJsonObject("generationConfig") {
            request.temperature?.let { put("temperature", it) }
            // Stream thought summaries so the connection isn't silent while the model thinks.
            // They are filtered out below and never shown or parsed.
            if (thoughts) putJsonObject("thinkingConfig") { put("includeThoughts", true) }
            val schema = request.schemaJson?.let { GeminiSchema.simplify(json.parseToJsonElement(it)) }
            if (schema != null && !withSchema) {
                put("responseMimeType", "application/json")
            } else if (schema != null) {
                when (field) {
                    SchemaField.RESPONSE_FORMAT -> putJsonObject("responseFormat") {
                        putJsonObject("text") {
                            put("mimeType", "APPLICATION_JSON")
                            put("schema", schema)
                        }
                    }
                    SchemaField.RESPONSE_JSON_SCHEMA -> {
                        put("responseMimeType", "application/json")
                        put("responseJsonSchema", schema)
                    }
                }
            }
        }
    }

    private fun rejectsSchemaField(body: String, field: SchemaField): Boolean {
        val needle = when (field) {
            SchemaField.RESPONSE_FORMAT -> listOf("responseFormat", "response_format")
            SchemaField.RESPONSE_JSON_SCHEMA -> listOf("responseJsonSchema", "response_json_schema")
        }
        return needle.any { body.contains(it) }
    }

    private fun SchemaField.other() = when (this) {
        SchemaField.RESPONSE_FORMAT -> SchemaField.RESPONSE_JSON_SCHEMA
        SchemaField.RESPONSE_JSON_SCHEMA -> SchemaField.RESPONSE_FORMAT
    }

    private fun <T> execute(request: Request, onSuccess: (Response) -> T): T {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw AiException.Network(id, e)
        }
        return response.use {
            if (!it.isSuccessful) throw errorFor(it.code, it.body.string())
            onSuccess(it)
        }
    }

    internal fun errorFor(code: Int, body: String): AiException {
        val error = runCatching { json.decodeFromString(ErrorEnvelopeDto.serializer(), body).error }.getOrNull()
        val message = error?.message ?: body.take(200).ifBlank { "HTTP $code" }
        val reasons = error?.details.orEmpty().mapNotNull { it["reason"]?.jsonPrimitive?.contentOrNull }
        return when {
            code == 429 || error?.status == "RESOURCE_EXHAUSTED" ->
                AiException.RateLimited(id, retryDelaySeconds(error), message)
            code == 401 || code == 403 || "API_KEY_INVALID" in reasons ||
                error?.status == "UNAUTHENTICATED" || error?.status == "PERMISSION_DENIED" ->
                AiException.Unauthorized(id, message)
            else -> AiException.Server(id, code, message)
        }
    }

    private fun retryDelaySeconds(error: ErrorDto?): Long? =
        error?.details.orEmpty()
            .firstOrNull { it["@type"]?.jsonPrimitive?.contentOrNull?.endsWith("google.rpc.RetryInfo") == true }
            ?.get("retryDelay")?.jsonPrimitive?.contentOrNull
            ?.removeSuffix("s")?.toDoubleOrNull()
            ?.let { kotlin.math.ceil(it).toLong() }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, text: String): T = try {
        json.decodeFromString(serializer, text)
    } catch (e: SerializationException) {
        throw AiException.BadResponse(id, e.message ?: "malformed JSON")
    } catch (e: IllegalArgumentException) {
        throw AiException.BadResponse(id, e.message ?: "malformed JSON")
    }

    private fun ModelDto.toModelInfo(): ModelInfo {
        val modelId = name.removePrefix("models/")
        val lower = modelId.lowercase()
        return ModelInfo(
            id = modelId,
            displayName = displayName ?: modelId,
            description = description.orEmpty(),
            inputTokenLimit = inputTokenLimit,
            outputTokenLimit = outputTokenLimit,
            isPreview = "preview" in lower || "exp" in lower,
        )
    }

    companion object {
        const val ID = "gemini"
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/"
        private const val KEY_HEADER = "x-goog-api-key"
        private val JSON_MEDIA = "application/json".toMediaType()
        private val BLOCKING_FINISH_REASONS = setOf("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "RECITATION")
    }
}
