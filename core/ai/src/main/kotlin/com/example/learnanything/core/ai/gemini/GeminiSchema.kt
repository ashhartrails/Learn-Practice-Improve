package com.example.learnanything.core.ai.gemini

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Gemini compiles the response schema into a decoding grammar, and length, count and range
 * limits can make that grammar too large ("too many states for serving"). We send Gemini a
 * schema with only the shape, and still validate every reply locally against the full schema.
 */
internal object GeminiSchema {
    private val DROPPED = setOf(
        "title", "minLength", "maxLength", "minItems", "maxItems", "minimum", "maximum",
        "exclusiveMinimum", "exclusiveMaximum", "pattern", "format", "multipleOf",
    )

    fun simplify(element: JsonElement, isPropertyMap: Boolean = false): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { isPropertyMap || it !in DROPPED }
                .mapValues { (key, value) -> simplify(value, isPropertyMap = !isPropertyMap && key == "properties") },
        )
        is JsonArray -> JsonArray(element.map { simplify(it) })
        else -> element
    }

    fun isTooComplex(body: String): Boolean = body.contains("too many states", ignoreCase = true)
}
