package com.example.learnanything.core.ai

import com.example.learnanything.core.ai.validation.JsonText
import com.example.learnanything.core.ai.validation.SchemaValidator
import com.example.learnanything.core.model.ai.PromptSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class CallProgress(val providerName: String, val attempt: Int, val charsReceived: Int)

sealed interface StructuredResult<out T> {
    data class Success<T>(
        val value: T,
        val providerId: String,
        val model: String?,
        val fromCache: Boolean,
    ) : StructuredResult<T>

    data class Failure(
        val kind: FailureKind,
        val message: String,
        val retryAfterSeconds: Long? = null,
    ) : StructuredResult<Nothing>
}

enum class FailureKind { NO_PROVIDER, UNAUTHORIZED, RATE_LIMITED, NETWORK, BUSY, BLOCKED, INVALID_OUTPUT, PROVIDER_ERROR }

/**
 * Runs one structured AI call end to end:
 * cache → provider → parse → schema check → code rules → retry once with the errors →
 * next provider. Only output that passes every check is returned or cached.
 */
@Singleton
class StructuredCaller @Inject constructor(
    private val router: ProviderRouter,
    private val validator: SchemaValidator,
    private val cache: AiCacheStore,
    private val json: Json,
) {
    suspend fun <T> call(
        spec: PromptSpec,
        serializer: KSerializer<T>,
        rules: (T) -> List<String> = { emptyList() },
        onProgress: (CallProgress) -> Unit = {},
    ): StructuredResult<T> {
        val schemaJson = spec.schema.load()
        val hash = PromptHasher.hash(spec)

        cache.get(hash)?.let { cached ->
            val checked = check(cached, schemaJson, serializer, rules)
            if (checked is Check.Ok) return StructuredResult.Success(checked.value, "cache", null, fromCache = true)
        }

        val providers = router.providersFor(spec.callType.tier)
        if (providers.isEmpty()) {
            return StructuredResult.Failure(FailureKind.NO_PROVIDER, "No AI provider is set up. Add a Gemini API key in Settings.")
        }

        var lastFailure: StructuredResult.Failure? = null
        for (provider in providers) {
            var prompt = spec.user
            for (attempt in 1..MAX_ATTEMPTS_PER_PROVIDER) {
                var busyRetries = 0
                var output: Output? = null
                while (output == null) {
                    try {
                        output = collect(provider, spec, schemaJson, prompt, attempt, onProgress)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: AiException) {
                        // A busy server (500/502/503/504) is usually gone in seconds: wait and retry.
                        if (e is AiException.Server && e.httpCode in BUSY_CODES && busyRetries < BUSY_BACKOFF_MS.size) {
                            delay(BUSY_BACKOFF_MS[busyRetries++])
                            continue
                        }
                        lastFailure = e.toFailure()
                        break
                    }
                }
                if (output == null) break // provider-level failure: move to the next provider
                when (val checked = check(output.text, schemaJson, serializer, rules)) {
                    is Check.Ok -> {
                        cache.put(hash, spec.callType, checked.canonical)
                        return StructuredResult.Success(checked.value, provider.id, output.model, fromCache = false)
                    }
                    is Check.Invalid -> {
                        lastFailure = StructuredResult.Failure(
                            FailureKind.INVALID_OUTPUT,
                            "The AI's answer did not pass validation: ${checked.errors.take(3).joinToString("; ")}",
                        )
                        prompt = retryPrompt(spec.user, checked.errors)
                    }
                }
            }
        }
        return lastFailure ?: StructuredResult.Failure(FailureKind.PROVIDER_ERROR, "The AI call failed.")
    }

    private data class Output(val text: String, val model: String?)

    private suspend fun collect(
        provider: AiProvider,
        spec: PromptSpec,
        schemaJson: String,
        prompt: String,
        attempt: Int,
        onProgress: (CallProgress) -> Unit,
    ): Output {
        val text = StringBuilder()
        var model: String? = null
        onProgress(CallProgress(provider.displayName, attempt, 0))
        provider.generate(
            AiRequest(spec.callType, spec.system, prompt, schemaJson),
        ).collect { chunk ->
            when (chunk) {
                is AiChunk.Text -> {
                    text.append(chunk.text)
                    onProgress(CallProgress(provider.displayName, attempt, text.length))
                }
                is AiChunk.Done -> model = chunk.model
            }
        }
        return Output(text.toString(), model)
    }

    private sealed interface Check<out T> {
        data class Ok<T>(val value: T, val canonical: String) : Check<T>
        data class Invalid(val errors: List<String>) : Check<Nothing>
    }

    private fun <T> check(
        raw: String,
        schemaJson: String,
        serializer: KSerializer<T>,
        rules: (T) -> List<String>,
    ): Check<T> {
        val element = when (val parsed = JsonText.parse(raw, json)) {
            is JsonText.Parsed.Error -> return Check.Invalid(listOf(parsed.message))
            is JsonText.Parsed.Ok -> parsed.element
        }
        val schemaErrors = validator.validate(schemaJson, element)
        if (schemaErrors.isNotEmpty()) return Check.Invalid(schemaErrors)
        val value = try {
            json.decodeFromJsonElement(serializer, element)
        } catch (e: SerializationException) {
            return Check.Invalid(listOf("Could not read the response: ${e.message}"))
        } catch (e: IllegalArgumentException) {
            return Check.Invalid(listOf("Could not read the response: ${e.message}"))
        }
        val ruleErrors = rules(value)
        if (ruleErrors.isNotEmpty()) return Check.Invalid(ruleErrors)
        // Cache the exact JSON that passed validation. Re-encoding could drop empty fields
        // and then fail the schema on the next read.
        return Check.Ok(value, element.toString())
    }

    private fun AiException.toFailure(): StructuredResult.Failure = when (this) {
        is AiException.NotConfigured -> StructuredResult.Failure(FailureKind.NO_PROVIDER, message.orEmpty())
        is AiException.Unauthorized -> StructuredResult.Failure(FailureKind.UNAUTHORIZED, message.orEmpty())
        is AiException.RateLimited -> StructuredResult.Failure(FailureKind.RATE_LIMITED, message.orEmpty(), retryAfterSeconds)
        is AiException.Network -> StructuredResult.Failure(FailureKind.NETWORK, message.orEmpty())
        is AiException.Blocked -> StructuredResult.Failure(FailureKind.BLOCKED, message.orEmpty())
        is AiException.Server ->
            if (httpCode in BUSY_CODES) {
                StructuredResult.Failure(FailureKind.BUSY, message.orEmpty())
            } else {
                StructuredResult.Failure(FailureKind.PROVIDER_ERROR, message.orEmpty())
            }
        is AiException.BadResponse ->
            StructuredResult.Failure(FailureKind.PROVIDER_ERROR, message.orEmpty())
    }

    companion object {
        /** First try plus one retry that includes the validation errors. */
        const val MAX_ATTEMPTS_PER_PROVIDER = 2
        private const val MAX_ERRORS_IN_RETRY = 10
        private val BUSY_CODES = setOf(500, 502, 503, 504)
        /** Waits before each retry when the server is busy. */
        private val BUSY_BACKOFF_MS = longArrayOf(2_000, 6_000)

        internal fun retryPrompt(original: String, errors: List<String>): String = buildString {
            append(original)
            append("\n\nYour previous reply was rejected because:\n")
            errors.take(MAX_ERRORS_IN_RETRY).forEach { append("- ").append(it).append('\n') }
            append("Reply again with JSON only, matching the schema, and fix every problem above.")
        }
    }
}
