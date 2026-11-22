package com.example.learnanything.core.model

import com.example.learnanything.core.model.learning.MasteryScheduler
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MasteryTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun correctAnswersRaiseMasteryAndClimbTheLadder() {
        var s = MasteryScheduler.initial(0)
        s = MasteryScheduler.update(s, true, 0)
        assertThat(s.mastery).isWithin(1e-9).of(0.35)
        assertThat(s.nextReviewAt).isEqualTo(3 * day)
        s = MasteryScheduler.update(s, true, 0)
        assertThat(s.nextReviewAt).isEqualTo(7 * day)
    }

    @Test fun wrongAnswerResetsTheLadder() {
        var s = MasteryScheduler.initial(0)
        repeat(3) { s = MasteryScheduler.update(s, true, 0) }
        s = MasteryScheduler.update(s, false, 0)
        assertThat(s.step).isEqualTo(0)
        assertThat(s.nextReviewAt).isEqualTo(1 * day)
        assertThat(s.mastery).isLessThan(0.7)
    }

    @Test fun ladderStopsAtTheTop() {
        var s = MasteryScheduler.initial(0)
        repeat(20) { s = MasteryScheduler.update(s, true, 0) }
        assertThat(s.nextReviewAt).isEqualTo(35 * day)
        assertThat(s.mastery).isAtMost(1.0)
    }

    @Test fun sessionMovesTheLadderOnce() {
        val s = MasteryScheduler.updateSession(MasteryScheduler.initial(0), listOf(true, true, true), 0)
        assertThat(s.step).isEqualTo(1)
        assertThat(s.mastery).isGreaterThan(0.7)
        val mixed = MasteryScheduler.updateSession(s, listOf(true, false), 0)
        assertThat(mixed.step).isEqualTo(0)
    }
}

class NextStepTest {
    private fun topic(id: String, done: Boolean) = Topic(id, 0, "T$id", emptyList(), emptyList(), 10, if (done) 1L else null)
    private fun outline(vararg chapters: Chapter) = SubjectOutline(
        Subject("s", "S", "g", Level.BEGINNER, 3, "", SubjectStatus.ACTIVE, 0), chapters.toList(),
    )

    @Test fun firstOpenTopicComesFirst() {
        val step = NextStep.of(outline(Chapter("c1", 0, "One", "g", listOf(topic("a", true), topic("b", false)))))
        assertThat(step).isEqualTo(NextStep.OpenTopic("b", "Tb", "One"))
    }

    @Test fun quizFollowsTheLastTopic() {
        val step = NextStep.of(outline(Chapter("c1", 0, "One", "g", listOf(topic("a", true)))))
        assertThat(step).isEqualTo(NextStep.TakeQuiz("c1", "One"))
    }

    @Test fun failedQuizStillBlocksTheNextChapter() {
        val c1 = Chapter("c1", 0, "One", "g", listOf(topic("a", true)), ChapterGate(0.5, false, 1, 1))
        val c2 = Chapter("c2", 1, "Two", "g", listOf(topic("b", false)))
        assertThat(NextStep.of(outline(c1, c2))).isEqualTo(NextStep.TakeQuiz("c1", "One"))
    }

    @Test fun passedChaptersAreSkippedAndAllDoneIsComplete() {
        val c1 = Chapter("c1", 0, "One", "g", listOf(topic("a", true)), ChapterGate(0.9, true, 1, 1))
        val c2 = Chapter("c2", 1, "Two", "g", listOf(topic("b", false)))
        assertThat(NextStep.of(outline(c1, c2))).isEqualTo(NextStep.OpenTopic("b", "Tb", "Two"))
        assertThat(NextStep.of(outline(c1))).isEqualTo(NextStep.CourseComplete)
    }
}
