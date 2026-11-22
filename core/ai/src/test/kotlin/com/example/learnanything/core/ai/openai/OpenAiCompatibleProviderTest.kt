package com.example.learnanything.core.ai.openai

import com.example.learnanything.core.ai.AiChunk
import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.AiRequest
import com.example.learnanything.core.model.ai.CallType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleProviderTest {
    private class Keys(var key: String? = "KEY", var model: String? = null) : CompatibleSettings {
        override suspend fun apiKey(providerId: String) = key
        override suspend fun selectedModel(providerId: String) = model
    }

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val keys = Keys()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private fun provider(spec: CompatibleSpec = OpenAiCompatibleProvider.GROQ) =
        OpenAiCompatibleProvider(spec, OkHttpClient(), keys, json, server.url("/v1/"))

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()
    private fun sse(vararg events: String) = events.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n"
    private val request = AiRequest(CallType.LESSON, "You teach.", "Teach joins.", """{"type":"object","properties":{"a":{"type":"string","maxLength":5}}}""")

    @Test fun listsChatModelsBiggestFirst() = runTest {
        server.enqueue(ok("""{"data":[{"id":"whisper-large-v3"},{"id":"llama-3.1-8b-instant"},{"id":"llama-3.3-70b-versatile"},{"id":"openai/gpt-oss-120b"},{"id":"llama-guard-4-12b"}]}"""))
        val models = provider().listModels().map { it.id }
        assertThat(models).containsExactly("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant").inOrder()
        assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer KEY")
    }

    @Test fun openRouterKeepsOnlyFreeModels() = runTest {
        server.enqueue(ok("""{"data":[
            {"id":"meta/llama-70b:free","pricing":{"prompt":"0","completion":"0"}},
            {"id":"paid/model-400b","pricing":{"prompt":"0.000002","completion":"0.000004"}},
            {"id":"qwen/qwen3-32b","pricing":{"prompt":"0","completion":"0"}}]}"""))
        val models = provider(OpenAiCompatibleProvider.OPENROUTER).listModels().map { it.id }
        assertThat(models).containsExactly("meta/llama-70b:free", "qwen/qwen3-32b").inOrder()
    }

    @Test fun streamsTextWithJsonSchemaAndTheFullSchemaInTheSystemMessage() = runTest {
        keys.model = "llama-3.3-70b-versatile"
        server.enqueue(ok(sse(
            """{"choices":[{"delta":{"content":"{\"a\":"}}]}""",
            """{"choices":[{"delta":{"content":"\"x\"}"},"finish_reason":"stop"}]}""",
        )))
        val chunks = provider().generate(request).toList()
        assertThat(chunks.filterIsInstance<AiChunk.Text>().joinToString("") { it.text }).isEqualTo("""{"a":"x"}""")
        assertThat((chunks.last() as AiChunk.Done).finishReason).isEqualTo("stop")

        val body = json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("llama-3.3-70b-versatile")
        assertThat(body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        // The provider gets the shape only; the limits are in the instructions.
        assertThat(body["response_format"].toString()).doesNotContain("maxLength")
        assertThat(body["messages"].toString()).contains("maxLength")
    }

    @Test fun fallsBackToJsonObjectWhenTheModelRejectsJsonSchema() = runTest {
        keys.model = "m"
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":{"message":"response_format json_schema is not supported by this model"}}""").build())
        server.enqueue(ok(sse("""{"choices":[{"delta":{"content":"{}"},"finish_reason":"stop"}]}""")))
        provider().generate(request).toList()
        server.takeRequest()
        val retry = json.parseToJsonElement(server.takeRequest().body!!.utf8()).jsonObject
        assertThat(retry["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("json_object")
    }

    @Test fun errorsMapToNormalStates() {
        keys.model = "m"
        server.enqueue(MockResponse.Builder().code(429).addHeader("retry-after", "7").body("""{"error":{"message":"Rate limit"}}""").build())
        val limited = assertThrows(AiException.RateLimited::class.java) { runBlocking { provider().generate(request).toList() } }
        assertThat(limited.retryAfterSeconds).isEqualTo(7)
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":{"message":"Invalid API Key"}}""").build())
        assertThrows(AiException.Unauthorized::class.java) { runBlocking { provider().generate(request).toList() } }
    }

    @Test fun withoutAKeyItIsNotConfigured() = runTest {
        keys.key = null
        assertThat(provider().isConfigured()).isFalse()
    }
}
