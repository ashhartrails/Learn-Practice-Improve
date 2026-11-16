package com.example.learnanything.core.model

import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.AnswerChecker
import com.example.learnanything.core.model.question.Question
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnswerCheckerTest {
    private val single = Question.SingleChoice("Which join keeps all left rows?", listOf("INNER", "LEFT", "CROSS"), 1, "LEFT keeps them.")
    private val multi = Question.MultiChoice("Which are joins?", listOf("INNER", "LEFT", "SELECT", "FULL"), setOf(0, 1, 3), "SELECT isn't.")
    private val numeric = Question.Numeric("g in m/s²?", 9.81, 0.05, "m/s²", "Standard gravity.")
    private val exactNumeric = Question.Numeric("2+2?", 4.0, 0.0, null, "Arithmetic.")
    private val text = Question.ShortText("Keyword to filter rows?", listOf("WHERE"), "WHERE filters.")

    @Test fun singleChoice() {
        assertThat(AnswerChecker.check(single, Answer.Choice(setOf(1))).correct).isTrue()
        assertThat(AnswerChecker.check(single, Answer.Choice(setOf(0))).correct).isFalse()
        assertThat(AnswerChecker.check(single, Answer.Choice(setOf(0, 1))).correct).isFalse()
    }

    @Test fun multiChoiceNeedsTheExactSet() {
        assertThat(AnswerChecker.check(multi, Answer.Choice(setOf(3, 1, 0))).correct).isTrue()
        assertThat(AnswerChecker.check(multi, Answer.Choice(setOf(0, 1))).correct).isFalse()
        assertThat(AnswerChecker.expected(multi)).isEqualTo("INNER, LEFT, FULL")
    }

    @Test fun numericWithinTolerance() {
        assertThat(AnswerChecker.check(numeric, Answer.Number("9.8")).correct).isTrue()
        assertThat(AnswerChecker.check(numeric, Answer.Number("9,85 m/s²")).correct).isTrue()
        assertThat(AnswerChecker.check(numeric, Answer.Number("9.7")).correct).isFalse()
        assertThat(AnswerChecker.check(numeric, Answer.Number("about ten")).correct).isFalse()
        assertThat(AnswerChecker.expected(numeric)).isEqualTo("9.81 m/s² (±0.05)")
    }

    @Test fun numericWithZeroToleranceIsExact() {
        assertThat(AnswerChecker.check(exactNumeric, Answer.Number("4")).correct).isTrue()
        assertThat(AnswerChecker.check(exactNumeric, Answer.Number("4.0001")).correct).isFalse()
        assertThat(AnswerChecker.expected(exactNumeric)).isEqualTo("4")
    }

    @Test fun shortTextIgnoresCaseSpacesAndTrailingPunctuation() {
        assertThat(AnswerChecker.check(text, Answer.Text("  where. ")).correct).isTrue()
        assertThat(AnswerChecker.check(text, Answer.Text("HAVING")).correct).isFalse()
    }

    @Test fun wrongAnswerTypeIsWrong() {
        assertThat(AnswerChecker.check(single, Answer.Text("LEFT")).correct).isFalse()
    }
}
