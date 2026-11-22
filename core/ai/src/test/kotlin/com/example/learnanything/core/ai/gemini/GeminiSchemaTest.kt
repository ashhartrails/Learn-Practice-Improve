package com.example.learnanything.core.ai.gemini

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class GeminiSchemaTest {
    @Test fun dropsLimitsButKeepsShapeAndPropertyNames() {
        val schema = Json.parseToJsonElement(
            """{"title":"X","type":"object","required":["title","maxItems"],"properties":{
              "title":{"type":"string","maxLength":10},
              "maxItems":{"type":"array","minItems":1,"items":{"type":"integer","minimum":3}}}}""",
        )
        val out = GeminiSchema.simplify(schema).toString()

        assertThat(out).isEqualTo(
            """{"type":"object","required":["title","maxItems"],"properties":{"title":{"type":"string"},""" +
                """"maxItems":{"type":"array","items":{"type":"integer"}}}}""",
        )
    }

    @Test fun recognisesTheTooComplexError() {
        assertThat(GeminiSchema.isTooComplex("The specified schema produces a constraint that has too many states for serving")).isTrue()
    }
}
