package com.example.learnanything.core.model

import com.example.learnanything.core.model.question.QuestionDto
import com.example.learnanything.core.model.question.QuestionRules
import com.example.learnanything.core.model.question.toQuestion
import com.example.learnanything.core.model.question.Question
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuestionRulesTest {
    private fun choice(kind: String, options: List<String>, correct: List<Int>) =
        QuestionDto(kind, "Pick the right one", options, correct, explanation = "Because.")

    @Test fun validSingleChoiceConverts() {
        val dto = choice("single_choice", listOf("A", "B", "C"), listOf(2))
        assertThat(QuestionRules.check(dto, "q")).isEmpty()
        assertThat((dto.toQuestion() as Question.SingleChoice).correctIndex).isEqualTo(2)
    }

    @Test fun singleChoiceWithTwoAnswersIsRejected() {
        assertThat(QuestionRules.check(choice("single_choice", listOf("A", "B"), listOf(0, 1)), "q").joinToString())
            .contains("exactly one")
    }

    @Test fun correctIndexOutOfRangeIsRejected() {
        assertThat(QuestionRules.check(choice("single_choice", listOf("A", "B"), listOf(5)), "q")).isNotEmpty()
    }

    @Test fun duplicateOptionsAreRejected() {
        assertThat(QuestionRules.check(choice("multi_choice", listOf("A", " a", "C"), listOf(0, 2)), "q").joinToString())
            .contains("duplicate")
    }

    @Test fun allOptionsCorrectIsRejected() {
        assertThat(QuestionRules.check(choice("multi_choice", listOf("A", "B"), listOf(0, 1)), "q")).isNotEmpty()
    }

    @Test fun numericNeedsANumber() {
        val dto = QuestionDto("numeric", "How many?", number = null, explanation = "Count them.")
        assertThat(QuestionRules.check(dto, "q").joinToString()).contains("number is missing")
        assertThat(QuestionRules.check(dto.copy(number = 3.0, tolerance = -1.0), "q").joinToString()).contains("tolerance")
        assertThat(QuestionRules.check(dto.copy(number = 3.0), "q")).isEmpty()
    }

    @Test fun shortTextNeedsShortAcceptedAnswers() {
        val dto = QuestionDto("short_text", "Keyword?", explanation = "It's WHERE.")
        assertThat(QuestionRules.check(dto, "q")).isNotEmpty()
        assertThat(QuestionRules.check(dto.copy(accepted = listOf("a very long sentence answer here")), "q")).isNotEmpty()
        assertThat(QuestionRules.check(dto.copy(accepted = listOf("WHERE")), "q")).isEmpty()
    }

    @Test fun unknownKindIsRejected() {
        assertThat(QuestionRules.check(QuestionDto("essay", "Discuss", explanation = "x"), "q")).isNotEmpty()
    }

    @Test fun urlsAreRejected() {
        val dto = choice("single_choice", listOf("See https://example.com", "B"), listOf(1))
        assertThat(QuestionRules.check(dto, "q").joinToString()).contains("URL")
        assertThat(TextSafety.containsUrl("visit example.com/docs")).isTrue()
        assertThat(TextSafety.containsUrl("Use SELECT name FROM users.")).isFalse()
    }
}
