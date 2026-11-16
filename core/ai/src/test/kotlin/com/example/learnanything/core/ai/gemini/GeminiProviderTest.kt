package com.example.learnanything.core.ai.gemini

import com.example.learnanything.core.ai.AiChunk
import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.AiRequest
import com.example.learnanything.core.model.ai.CallType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class GeminiProviderTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private var key: String? = "test-key"
    private var model: String? = "nova-flash"
    private lateinit var provider: GeminiProvider

    private val settings = object : GeminiSettings {
        override suspend fun apiKey() = key
        override suspend fun selectedModel() = model
    }

    private val request = AiRequest(CallType.OUTLINE, "sys", "user prompt", """{"type":"object"}""")

    @Before fun setUp() {
        server.start()
        provider = GeminiProvider(OkHttpClient(), settings, json, server.url("/"))
    }

    @After fun tearDown() = server.close()

    private fun sse(vararg events: String) = events.joinToString("") { "data: $it\r\n\r\n" }

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
    private fun status(code: Int, body: String) = MockResponse.Builder().code(code).body(body).build()

    @Test fun streamsTextAndSendsKeyAndSchema() = runTest {
        server.enqueue(ok(sse(
            """{"candidates":[{"content":{"parts":[{"text":"{\"a\":"}]}}]}""",
            """{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"1}"}]},"finishReason":"STOP"}]}""",
        )))

        val chunks = provider.generate(request).toList()

        val text = chunks.filterIsInstance<AiChunk.Text>().joinToString("") { it.text }
        assertThat(text).isEqualTo("{\"a\":1}")
        assertThat(chunks.last()).isEqualTo(AiChunk.Done("nova-flash", "STOP"))

        val recorded = server.takeRequest()
        assertThat(recorded.headers["x-goog-api-key"]).isEqualTo("test-key")
        assertThat(recorded.url.encodedPath).isEqualTo("/v1beta/models/nova-flash:streamGenerateContent")
        assertThat(recorded.url.queryParameter("alt")).isEqualTo("sse")
        val body = json.parseToJsonElement(recorded.body!!.utf8()).jsonObject
        val format = body["generationConfig"]!!.jsonObject["responseFormat"]!!.jsonObject["text"]!!.jsonObject
        assertThat(format["mimeType"].toString()).contains("APPLICATION_JSON")
        assertThat(format["schema"]).isNotNull()
    }

    @Test fun rejectedSchemaFieldSwitchesToJsonSchemaFieldOnce() = runTest {
        server.enqueue(status(400, """{"error":{"code":400,"message":"Invalid JSON payload received. Unknown name \"responseFormat\" at 'generation_config'","status":"INVALID_ARGUMENT"}}"""))
        server.enqueue(ok(sse("""{"candidates":[{"content":{"parts":[{"text":"{}"}]},"finishReason":"STOP"}]}""")))

        provider.generate(request).toList()

        server.takeRequest()
        val retry = json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        val config = retry["generationConfig"]!!.jsonObject
        assertThat(config.keys).containsAtLeast("responseMimeType", "responseJsonSchema")
        assertThat(provider.schemaField).isEqualTo(GeminiProvider.SchemaField.RESPONSE_JSON_SCHEMA)
    }

    @Test fun rateLimitCarriesRetryDelay() = runTest {
        server.enqueue(status(429, """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"17.4s"}]}}"""))

        val error = runCatching { provider.generate(request).toList() }.exceptionOrNull()
        assertThat(error).isInstanceOf(AiException.RateLimited::class.java)
        assertThat((error as AiException.RateLimited).retryAfterSeconds).isEqualTo(18)
    }

    @Test fun invalidKeyIsUnauthorized() = runTest {
        server.enqueue(status(400, """{"error":{"code":400,"message":"API key not valid.","status":"INVALID_ARGUMENT","details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID"}]}}"""))
        val error = runCatching { provider.listModelsWithKey("bad") }.exceptionOrNull()
        assertThat(error).isInstanceOf(AiException.Unauthorized::class.java)
    }

    @Test fun blockedPromptIsReported() = runTest {
        server.enqueue(ok(sse("""{"promptFeedback":{"blockReason":"SAFETY"}}""")))
        val error = runCatching { provider.generate(request).toList() }.exceptionOrNull()
        assertThat(error).isInstanceOf(AiException.Blocked::class.java)
    }

    @Test fun missingKeyIsNotConfigured() = runTest {
        key = null
        assertThat(provider.isConfigured()).isFalse()
        val error = runCatching { provider.generate(request).toList() }.exceptionOrNull()
        assertThat(error).isInstanceOf(AiException.NotConfigured::class.java)
    }

    @Test fun listModelsFollowsPagesAndFiltersToGenerateContent() = runTest {
        server.enqueue(ok("""{"models":[
            {"name":"models/nova-flash","displayName":"Nova Flash","supportedGenerationMethods":["generateContent","countTokens"]},
            {"name":"models/text-embed","supportedGenerationMethods":["embedContent"]}
        ],"nextPageToken":"p2"}"""))
        server.enqueue(ok("""{"models":[{"name":"models/nova-pro-preview","supportedGenerationMethods":["generateContent"]}]}"""))

        val models = provider.listModels()

        assertThat(models.map { it.id }).containsExactly("nova-flash", "nova-pro-preview").inOrder()
        assertThat(models.last().isPreview).isTrue()
        server.takeRequest()
        assertThat(server.takeRequest().url.queryParameter("pageToken")).isEqualTo("p2")
    }

    @Test fun noSelectedModelAutoPicksFromLiveList() = runTest {
        model = null
        server.enqueue(ok("""{"models":[
            {"name":"models/nova-flash-lite","supportedGenerationMethods":["generateContent"]},
            {"name":"models/nova-flash","supportedGenerationMethods":["generateContent"]}
        ]}"""))
        server.enqueue(ok(sse("""{"candidates":[{"content":{"parts":[{"text":"{}"}]},"finishReason":"STOP"}]}""")))

        val done = provider.generate(request).toList().last() as AiChunk.Done
        assertThat(done.model).isEqualTo("nova-flash")
    }

    @Test fun serverErrorIsReported() {
        server.enqueue(status(500, "oops"))
        assertThrows(AiException.Server::class.java) {
            kotlinx.coroutines.runBlocking { provider.generate(request).toList() }
        }
    }
}
