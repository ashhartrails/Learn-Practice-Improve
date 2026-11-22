package com.example.learnanything.core.model

import com.example.learnanything.core.model.clarify.ClarifyQuestion
import com.example.learnanything.core.model.clarify.ClarifyResponse
import com.example.learnanything.core.model.clarify.ClarifyRules
import com.example.learnanything.core.model.lesson.LessonCard
import com.example.learnanything.core.model.lesson.LessonResponse
import com.example.learnanything.core.model.lesson.LessonRules
import com.example.learnanything.core.model.lesson.WorkedExample
import com.example.learnanything.core.model.outline.OutlinePrompt
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.clarify.Clarification
import com.example.learnanything.core.model.question.QuestionDto
import com.example.learnanything.core.model.quiz.QuizQuestion
import com.example.learnanything.core.model.quiz.QuizResponse
import com.example.learnanything.core.model.quiz.QuizRules
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CallRulesTest {
    private val q = QuestionDto("single_choice", "Which one?", listOf("A", "B", "C"), listOf(0), explanation = "A.")

    @Test fun clarifyNeedsUniqueIdsAndOptionsForChoices() {
        val ok = ClarifyResponse(listOf(ClarifyQuestion("why", "Why learn this?", "choice", listOf("Work", "Fun")), ClarifyQuestion("tools", "Which database?", "text")))
        assertThat(ClarifyRules.check(ok)).isEmpty()
        val bad = ClarifyResponse(listOf(ClarifyQuestion("a", "Q one?", "choice", listOf("x")), ClarifyQuestion("a", "Q two?", "text")))
        assertThat(ClarifyRules.check(bad)).hasSize(2)
    }

    @Test fun lessonChecksQuestionsAndUrls() {
        val body = (1..80).joinToString(" ") { "word$it" }
        val cards = (1..5).map { LessonCard("Idea $it", body) }
        val lesson = LessonResponse("It matters.", cards, WorkedExample("P", listOf("S"), "A"), q, listOf(q, q.copy(prompt = "Another?")))
        assertThat(LessonRules.check(lesson)).isEmpty()
        val bad = lesson.copy(cards = cards.drop(1) + LessonCard("Idea", "Read www.example.com $body"), checks = listOf(q, q))
        assertThat(LessonRules.check(bad)).hasSize(2)
        val short = lesson.copy(cards = cards.take(3).map { it.copy(body = "Too short.") })
        assertThat(LessonRules.check(short).joinToString()).contains("at least 5 cards")
        assertThat(LessonRules.check(short).joinToString()).contains("too short")
    }

    @Test fun quizMustCoverEveryTopicWithValidIndexes() {
        val ok = QuizResponse(listOf(QuizQuestion(0, q), QuizQuestion(1, q.copy(prompt = "Second?"))))
        assertThat(QuizRules.check(ok, topicCount = 2)).isEmpty()
        val missing = QuizResponse(listOf(QuizQuestion(0, q), QuizQuestion(0, q.copy(prompt = "Second?"))))
        assertThat(QuizRules.check(missing, topicCount = 2).joinToString()).contains("missing topicIndex 1")
        val outOfRange = QuizResponse(listOf(QuizQuestion(7, q)))
        assertThat(QuizRules.check(outOfRange, topicCount = 2)).isNotEmpty()
    }

    @Test fun outlinePromptIncludesClarifications() {
        val spec = OutlinePrompt.build(OutlineRequest("SQL", Level.BEGINNER, 3), listOf(Clarification("Which database?", "PostgreSQL")))
        assertThat(spec.user).contains("PostgreSQL")
        assertThat(spec.user).endsWith("Build the course outline.")
    }
}
