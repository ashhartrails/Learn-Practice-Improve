package com.example.learnanything.core.ai

import com.example.learnanything.core.ai.fakes.InMemoryCache
import com.example.learnanything.core.ai.fakes.ScriptedProvider
import com.example.learnanything.core.ai.fakes.rateLimited
import com.example.learnanything.core.ai.validation.JsonSchemaValidator
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.outline.OutlinePrompt
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineRules
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class StructuredCallerTest {
    private val spec = OutlinePrompt.build(OutlineRequest("SQL joins", Level.BEGINNER, 3))
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = InMemoryCache()

    private fun caller(vararg providers: AiProvider) =
        StructuredCaller(ProviderRouter(providers.toList()), JsonSchemaValidator(), cache, json)

    private suspend fun StructuredCaller.outline() =
        call(spec, OutlineResponse.serializer(), OutlineRules::check)

    @Test fun validFirstReplySucceedsAndIsCached() = runTest {
        val provider = ScriptedProvider("p1", Samples.VALID_OUTLINE)
        val result = caller(provider).outline()

        assertThat(result).isInstanceOf(StructuredResult.Success::class.java)
        val success = result as StructuredResult.Success
        assertThat(success.value.chapters.single().topics).hasSize(2)
        assertThat(success.fromCache).isFalse()
        assertThat(cache.entries).hasSize(1)
        assertThat(provider.requests.single().schemaJson).contains("\"chapters\"")
    }

    @Test fun invalidReplyIsRetriedOnceWithTheErrors() = runTest {
        val provider = ScriptedProvider("p1", """{"title":"x"}""", Samples.VALID_OUTLINE)
        val result = caller(provider).outline()

        assertThat(result).isInstanceOf(StructuredResult.Success::class.java)
        assertThat(provider.requests).hasSize(2)
        assertThat(provider.requests[1].prompt).contains("Your previous reply was rejected")
        assertThat(provider.requests[1].prompt).contains("summary")
    }

    @Test fun ruleViolationIsRetriedToo() = runTest {
        val provider = ScriptedProvider("p1", Samples.DUPLICATE_TOPICS, Samples.VALID_OUTLINE)
        val result = caller(provider).outline()

        assertThat(result).isInstanceOf(StructuredResult.Success::class.java)
        assertThat(provider.requests[1].prompt).contains("duplicate topic title")
    }

    @Test fun twoInvalidRepliesFallBackToNextProvider() = runTest {
        val first = ScriptedProvider("p1", "not json", "still not json")
        val second = ScriptedProvider("p2", Samples.VALID_OUTLINE)
        val result = caller(first, second).outline() as StructuredResult.Success

        assertThat(first.requests).hasSize(2)
        assertThat(result.providerId).isEqualTo("p2")
    }

    @Test fun twoInvalidRepliesWithNoFallbackFailAndCacheNothing() = runTest {
        val provider = ScriptedProvider("p1", "not json", """{"title":"x"}""")
        val result = caller(provider).outline()

        assertThat(result).isInstanceOf(StructuredResult.Failure::class.java)
        assertThat((result as StructuredResult.Failure).kind).isEqualTo(FailureKind.INVALID_OUTPUT)
        assertThat(cache.entries).isEmpty()
    }

    @Test fun rateLimitSkipsToNextProviderWithoutRetry() = runTest {
        val first = ScriptedProvider("p1", rateLimited("p1"))
        val second = ScriptedProvider("p2", Samples.VALID_OUTLINE)
        val result = caller(first, second).outline() as StructuredResult.Success

        assertThat(first.requests).hasSize(1)
        assertThat(result.providerId).isEqualTo("p2")
    }

    @Test fun rateLimitOnOnlyProviderIsReportedWithRetryAfter() = runTest {
        val result = caller(ScriptedProvider("p1", rateLimited("p1"))).outline() as StructuredResult.Failure

        assertThat(result.kind).isEqualTo(FailureKind.RATE_LIMITED)
        assertThat(result.retryAfterSeconds).isEqualTo(30)
    }

    @Test fun busyServerIsRetriedThenSucceeds() = runTest {
        val busy = AiException.Server("p1", 503, "high demand")
        val provider = ScriptedProvider("p1", busy, busy, Samples.VALID_OUTLINE)
        val result = caller(provider).outline() as StructuredResult.Success

        assertThat(provider.requests).hasSize(3)
        assertThat(result.providerId).isEqualTo("p1")
    }

    @Test fun busyServerThatStaysBusyReportsBusy() = runTest {
        val busy = AiException.Server("p1", 503, "high demand")
        val result = caller(ScriptedProvider("p1", busy, busy, busy)).outline() as StructuredResult.Failure

        assertThat(result.kind).isEqualTo(FailureKind.BUSY)
    }

    @Test fun cacheHitSkipsTheProvider() = runTest {
        caller(ScriptedProvider("p1", Samples.VALID_OUTLINE)).outline()
        val second = ScriptedProvider("p2")
        val result = caller(second).outline() as StructuredResult.Success

        assertThat(result.fromCache).isTrue()
        assertThat(second.requests).isEmpty()
    }

    @Test fun corruptCacheEntryIsIgnored() = runTest {
        cache.entries[PromptHasher.hash(spec)] = "{broken"
        val result = caller(ScriptedProvider("p1", Samples.VALID_OUTLINE)).outline() as StructuredResult.Success
        assertThat(result.fromCache).isFalse()
    }

    @Test fun unconfiguredProvidersMeanNoProvider() = runTest {
        val result = caller(ScriptedProvider("p1", configured = false)).outline() as StructuredResult.Failure
        assertThat(result.kind).isEqualTo(FailureKind.NO_PROVIDER)
    }

    @Test fun cachedResponseStillPassesSchemaWhenItHasEmptyArrays() = runTest {
        // The cache must hold the exact validated JSON (empty arrays included). Re-encoding
        // would drop defaulted fields such as QuestionDto.options and fail the schema on read.
        caller(ScriptedProvider("p1", Samples.VALID_OUTLINE)).outline()
        assertThat(cache.entries.values.single()).contains("\"prerequisites\":[]")
        val result = caller(ScriptedProvider("p2")).outline() as StructuredResult.Success
        assertThat(result.fromCache).isTrue()
    }
}
