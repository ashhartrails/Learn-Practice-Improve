package com.example.learnanything.core.ai.validation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class JsonTextTest {
    @Test fun plainJsonParses() {
        assertThat(JsonText.parse("""{"a":1}""")).isInstanceOf(JsonText.Parsed.Ok::class.java)
    }

    @Test fun markdownFenceIsStripped() {
        val parsed = JsonText.parse("```json\n{\"a\":1}\n```")
        assertThat(parsed).isInstanceOf(JsonText.Parsed.Ok::class.java)
    }

    @Test fun surroundingProseIsStripped() {
        val parsed = JsonText.parse("Here you go: {\"a\": {\"b\": 2}} Hope that helps")
        assertThat(parsed).isInstanceOf(JsonText.Parsed.Ok::class.java)
    }

    @Test fun truncatedJsonIsAnError() {
        assertThat(JsonText.parse("""{"a": [1, 2""")).isInstanceOf(JsonText.Parsed.Error::class.java)
    }

    @Test fun noJsonIsAnError() {
        val parsed = JsonText.parse("Sorry, I can't help with that.")
        assertThat((parsed as JsonText.Parsed.Error).message).contains("no JSON")
    }
}
