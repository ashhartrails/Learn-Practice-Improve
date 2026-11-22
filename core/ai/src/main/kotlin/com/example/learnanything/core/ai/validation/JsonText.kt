package com.example.learnanything.core.ai.validation

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Turns raw model text into a JSON value. Tolerates a markdown code fence or stray
 * text around a single JSON object (the result is still schema-validated afterwards).
 */
object JsonText {
    sealed interface Parsed {
        data class Ok(val element: JsonElement) : Parsed
        data class Error(val message: String) : Parsed
    }

    fun parse(raw: String, json: Json = Json): Parsed {
        var text = raw.trim().removePrefix("﻿")
        if (text.startsWith("```")) {
            text = text.substringAfter('\n', "").substringBeforeLast("```").trim()
        }
        if (!text.startsWith("{")) {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return Parsed.Error("Response contains no JSON object")
            text = text.substring(start, end + 1)
        }
        return try {
            Parsed.Ok(json.parseToJsonElement(text))
        } catch (e: SerializationException) {
            Parsed.Error("Response is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}")
        }
    }
}
