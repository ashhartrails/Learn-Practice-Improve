package com.example.learnanything.core.model.learning

/** A concept's learning state. M1 has one concept per topic. */
data class ConceptState(
    /** 0.0 (not known) to 1.0 (mastered). */
    val mastery: Double,
    /** Position on the review ladder. */
    val step: Int,
    val nextReviewAt: Long,
)

/**
 * Mastery is an exponential moving average of correct answers.
 * Reviews climb a fixed ladder of days; a wrong answer drops back to the first rung.
 */
object MasteryScheduler {
    val LADDER_DAYS = listOf(1, 3, 7, 16, 35)
    const val ALPHA = 0.35
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun initial(now: Long) = ConceptState(mastery = 0.0, step = 0, nextReviewAt = now + DAY_MS)

    fun update(state: ConceptState, correct: Boolean, now: Long): ConceptState {
        val target = if (correct) 1.0 else 0.0
        val mastery = (state.mastery + ALPHA * (target - state.mastery)).coerceIn(0.0, 1.0)
        val step = if (correct) (state.step + 1).coerceAtMost(LADDER_DAYS.lastIndex) else 0
        return ConceptState(mastery, step, now + LADDER_DAYS[step] * DAY_MS)
    }

    /**
     * One study session with several answers (e.g. a topic's check questions or a quiz).
     * Mastery moves once per answer; the review ladder moves once per session:
     * up a rung if every answer was right, back to the start otherwise.
     */
    fun updateSession(state: ConceptState, results: List<Boolean>, now: Long): ConceptState {
        if (results.isEmpty()) return state
        val mastery = results.fold(state.mastery) { m, r -> (m + ALPHA * ((if (r) 1.0 else 0.0) - m)).coerceIn(0.0, 1.0) }
        val step = if (results.all { it }) (state.step + 1).coerceAtMost(LADDER_DAYS.lastIndex) else 0
        return ConceptState(mastery, step, now + LADDER_DAYS[step] * DAY_MS)
    }
}
