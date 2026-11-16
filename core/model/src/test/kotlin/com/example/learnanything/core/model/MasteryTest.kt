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
