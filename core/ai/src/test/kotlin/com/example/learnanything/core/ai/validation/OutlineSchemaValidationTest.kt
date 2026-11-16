package com.example.learnanything.core.ai.validation

import com.example.learnanything.core.ai.Samples
import com.example.learnanything.core.model.ai.AiSchema
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class OutlineSchemaValidationTest {
    private val validator = JsonSchemaValidator()
    private val schema = AiSchema.OUTLINE_V1.load()

    private fun errors(json: String): List<String> = validator.validate(schema, Json.parseToJsonElement(json))

    private fun mutate(transform: (String) -> String) = errors(transform(Samples.VALID_OUTLINE))

    @Test fun validOutlinePasses() {
        assertThat(errors(Samples.VALID_OUTLINE)).isEmpty()
    }

    @Test fun missingRequiredFieldFails() {
        val result = mutate { it.replace("\"summary\": \"Combine tables confidently with inner, outer and self joins.\",", "") }
        assertThat(result).isNotEmpty()
        assertThat(result.joinToString()).contains("summary")
    }

    @Test fun wrongTypeFails() {
        assertThat(mutate { it.replace("\"estimatedMinutes\": 10", "\"estimatedMinutes\": \"ten\"") }).isNotEmpty()
    }

    @Test fun nonIntegerMinutesFails() {
        assertThat(mutate { it.replace("\"estimatedMinutes\": 10", "\"estimatedMinutes\": 10.5") }).isNotEmpty()
    }

    @Test fun outOfRangeMinutesFails() {
        assertThat(mutate { it.replace("\"estimatedMinutes\": 10", "\"estimatedMinutes\": 600") }).isNotEmpty()
    }

    @Test fun extraPropertyFails() {
        val result = mutate { it.replaceFirst("\"title\": \"SQL joins from scratch\",", "\"title\": \"SQL joins from scratch\", \"url\": \"https://example.com\",") }
        assertThat(result).isNotEmpty()
    }

    @Test fun emptyChaptersFails() {
        assertThat(errors("""{"title":"SQL joins","summary":"A summary long enough.","chapters":[]}""")).isNotEmpty()
    }

    @Test fun emptyObjectivesFails() {
        assertThat(mutate { it.replace("[\"Write an INNER JOIN\"]", "[]") }).isNotEmpty()
    }

    @Test fun tooShortTitleFails() {
        // Gemini ignores minLength; the local validator must still enforce it.
        assertThat(mutate { it.replace("\"SQL joins from scratch\"", "\"S\"") }).isNotEmpty()
    }

    @Test fun nullInsteadOfArrayFails() {
        assertThat(mutate { it.replace("\"prerequisites\": []", "\"prerequisites\": null") }).isNotEmpty()
    }

    @Test fun nonObjectRootFails() {
        assertThat(validator.validate(schema, Json.parseToJsonElement("[1,2,3]"))).isNotEmpty()
    }
}
