package com.example.learnanything.core.ai.validation

import com.example.learnanything.core.model.ai.AiSchema
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** Every M1 schema: one valid sample passes, and typical model mistakes fail. */
class CallSchemasValidationTest {
    private val validator = JsonSchemaValidator()
    private fun errors(schema: AiSchema, json: String) = validator.validate(schema.load(), Json.parseToJsonElement(json))

    private val q = """{"kind":"single_choice","prompt":"Which join keeps all left rows?","options":["INNER","LEFT","CROSS"],"correct":[1],"accepted":[],"explanation":"LEFT keeps unmatched left rows."}"""
    private val numeric = """{"kind":"numeric","prompt":"How many rows result?","options":[],"correct":[],"number":12,"tolerance":0,"unit":null,"accepted":[],"explanation":"Three times four."}"""

    val lesson = """
        {"whyItMatters":"Joins let you answer questions across tables.",
         "cards":[{"heading":"What a join is","body":"A join combines rows from two tables."},{"heading":"Keys","body":"Rows match on a **key** column."}],
         "workedExample":{"problem":"List each order with its customer name.","steps":["Pick the tables","Match on customer_id"],"answer":"SELECT ..."},
         "tryIt":$q,
         "checks":[$q,$numeric]}
    """.trimIndent()

    @Test fun clarifyValidPasses() {
        assertThat(errors(AiSchema.CLARIFY_V1, """{"questions":[{"id":"why","question":"Why are you learning this?","kind":"choice","options":["Work","Study"]},{"id":"db","question":"Which database do you use?","kind":"text","options":[]}]}""")).isEmpty()
    }

    @Test fun clarifyWithOneQuestionFails() {
        assertThat(errors(AiSchema.CLARIFY_V1, """{"questions":[{"id":"why","question":"Why are you learning this?","kind":"choice","options":["Work","Study"]}]}""")).isNotEmpty()
    }

    @Test fun clarifyUnknownKindFails() {
        assertThat(errors(AiSchema.CLARIFY_V1, """{"questions":[{"id":"a","question":"Question one?","kind":"slider","options":[]},{"id":"b","question":"Question two?","kind":"text","options":[]}]}""")).isNotEmpty()
    }

    @Test fun lessonValidPasses() {
        assertThat(errors(AiSchema.LESSON_V1, lesson)).isEmpty()
    }

    @Test fun lessonWithOneCheckFails() {
        assertThat(errors(AiSchema.LESSON_V1, lesson.replace("[$q,$numeric]", "[$q]"))).isNotEmpty()
    }

    @Test fun lessonQuestionWithUnknownKindFails() {
        assertThat(errors(AiSchema.LESSON_V1, lesson.replaceFirst("\"single_choice\"", "\"essay\""))).isNotEmpty()
    }

    @Test fun lessonQuestionMissingExplanationFails() {
        val noExplanation = q.replace(""","explanation":"LEFT keeps unmatched left rows."""", "")
        assertThat(errors(AiSchema.LESSON_V1, lesson.replaceFirst(q, noExplanation))).isNotEmpty()
    }

    @Test fun lessonNegativeToleranceFails() {
        assertThat(errors(AiSchema.LESSON_V1, lesson.replace("\"tolerance\":0", "\"tolerance\":-1"))).isNotEmpty()
    }

    @Test fun quizValidPasses() {
        val quiz = (0 until 6).joinToString(",", "{\"questions\":[", "]}") { """{"topicIndex":${it % 2},"question":$q}""" }
        assertThat(errors(AiSchema.QUIZ_V1, quiz)).isEmpty()
    }

    @Test fun quizTooShortFails() {
        assertThat(errors(AiSchema.QUIZ_V1, """{"questions":[{"topicIndex":0,"question":$q}]}""")).isNotEmpty()
    }

    @Test fun quizNegativeTopicIndexFails() {
        val quiz = (0 until 6).joinToString(",", "{\"questions\":[", "]}") { """{"topicIndex":-1,"question":$q}""" }
        assertThat(errors(AiSchema.QUIZ_V1, quiz)).isNotEmpty()
    }
}
