package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.CallProgress
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.StructuredResult
import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.StoredPractical
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.ModelJson
import com.example.learnanything.core.model.PracticalResult
import com.example.learnanything.core.model.practical.CodeRunner
import com.example.learnanything.core.model.practical.GradePrompt
import com.example.learnanything.core.model.practical.GradeResponse
import com.example.learnanything.core.model.practical.GradeRules
import com.example.learnanything.core.model.practical.PracticalContext
import com.example.learnanything.core.model.practical.PracticalKind
import com.example.learnanything.core.model.practical.PracticalPrompt
import com.example.learnanything.core.model.practical.PracticalResponse
import com.example.learnanything.core.model.practical.PracticalRules
import com.example.learnanything.core.model.practical.PracticalVerifier
import com.example.learnanything.core.model.practical.RubricScore
import com.example.learnanything.core.model.practical.RunReport
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.AnswerChecker
import com.example.learnanything.core.model.question.Question
import javax.inject.Inject

object Trust {
    const val EXACT = "EXACT"
    const val AI_GRADED = "AI_GRADED"
}

sealed interface PracticalLoad {
    data class Ready(val place: QuizPlace, val stored: StoredPractical, val lastAnswer: String?) : PracticalLoad
    data class Failed(val message: UserMessage) : PracticalLoad
}

sealed interface PracticalOutcome {
    data class Graded(
        val passed: Boolean,
        val score: Double,
        val trust: String,
        /** Code kinds. */
        val report: RunReport? = null,
        /** Numeric kind: the expected answer, readable. */
        val expected: String? = null,
        /** Written kind. */
        val grade: GradeResponse? = null,
        val rubricScore: RubricScore? = null,
    ) : PracticalOutcome

    data class Failed(val message: UserMessage) : PracticalOutcome
}

/**
 * Returns the chapter's practical, generating one when there is none, when [newTask] is set,
 * or when the learner asks for a different [kind]. Code tasks are generated, then validated:
 * a task is discarded if its reference solution fails its own tests.
 */
class GetPracticalUseCase @Inject constructor(
    private val findChapter: FindChapterUseCase,
    private val caller: StructuredCaller,
    private val learning: LearningRepository,
    private val runner: CodeRunner,
    private val time: TimeSource,
) {
    suspend operator fun invoke(
        chapterId: String,
        newTask: Boolean = false,
        kind: PracticalKind? = null,
        onProgress: (CallProgress) -> Unit = {},
    ): PracticalLoad {
        val place = findChapter(chapterId) ?: return PracticalLoad.Failed(UserMessage("This chapter no longer exists."))
        if (!newTask && kind == null) {
            learning.practical(chapterId)?.let { return PracticalLoad.Ready(place, it, learning.lastPracticalAnswer(it.itemId)) }
        }
        val firstTopic = place.chapter.topics.firstOrNull()
            ?: return PracticalLoad.Failed(UserMessage("This chapter has no topics."))
        val variant = learning.practicalVariant(chapterId) + 1
        var problem: String? = null
        repeat(MAX_GENERATIONS) { attempt ->
            val spec = PracticalPrompt.build(
                PracticalContext(
                    subjectTitle = place.subject.title,
                    subjectGoal = place.subject.goal,
                    level = place.subject.level,
                    clarifications = place.subject.clarifications,
                    chapterTitle = place.chapter.title,
                    chapterGoal = place.chapter.goal,
                    topics = place.chapter.topics.map { it.title },
                    requestedKind = kind,
                    // The time makes each request unique, so a task we threw away is never served from the cache.
                    variant = variant,
                    previousProblem = problem,
                ),
            ).let { it.copy(user = it.user + "\nRequest ${time.now()}-$attempt.") }
            val rules: (PracticalResponse) -> List<String> = { PracticalRules.check(it, kind) }
            when (val r = caller.call(spec, PracticalResponse.serializer(), rules, onProgress)) {
                is StructuredResult.Failure ->
                    return PracticalLoad.Failed(FailureMessages.of(r.kind, r.message, r.retryAfterSeconds))
                is StructuredResult.Success -> when (val verdict = PracticalVerifier.verify(r.value, runner)) {
                    PracticalVerifier.Verdict.Ok -> {
                        val stored = learning.savePractical(chapterId, firstTopic.id, r.value, variant)
                        return PracticalLoad.Ready(place, stored, null)
                    }
                    is PracticalVerifier.Verdict.Rejected -> problem = verdict.reason
                }
            }
        }
        return PracticalLoad.Failed(
            UserMessage("The AI couldn't write a task whose own solution passes its tests ($problem). Try again, or pick another type."),
        )
    }

    companion object {
        const val MAX_GENERATIONS = 2
    }
}

/** Checks a practical answer: by code for code, SQL and numbers; by rubric for written answers. */
class SubmitPracticalUseCase @Inject constructor(
    private val runner: CodeRunner,
    private val caller: StructuredCaller,
    private val learning: LearningRepository,
    private val subjects: SubjectRepository,
    private val time: TimeSource,
) {
    suspend operator fun invoke(
        place: QuizPlace,
        stored: StoredPractical,
        answer: String,
        onProgress: (CallProgress) -> Unit = {},
    ): PracticalOutcome {
        val p = stored.practical
        val outcome: PracticalOutcome.Graded = when (p.kind) {
            PracticalKind.PYTHON, PracticalKind.JAVASCRIPT, PracticalKind.SQL -> {
                val report = runner.run(p, answer)
                val score = if (report.tests.isEmpty() || report.error != null) 0.0
                else report.tests.count { it.passed }.toDouble() / report.tests.size
                PracticalOutcome.Graded(report.allPassed, score, Trust.EXACT, report = report)
            }
            PracticalKind.NUMERIC -> {
                val question = Question.Numeric(p.task, p.number ?: 0.0, p.tolerance ?: 0.0, p.unit?.takeIf { it.isNotBlank() }, "")
                val result = AnswerChecker.check(question, Answer.Number(answer))
                PracticalOutcome.Graded(result.correct, result.score, Trust.EXACT, expected = result.expected)
            }
            PracticalKind.WRITTEN -> {
                if (answer.isBlank()) return PracticalOutcome.Failed(UserMessage("Write your answer first."))
                val rules: (GradeResponse) -> List<String> = { GradeRules.check(it, p.rubric, answer) }
                when (val r = caller.call(GradePrompt.build(p, answer), GradeResponse.serializer(), rules, onProgress)) {
                    is StructuredResult.Failure ->
                        return PracticalOutcome.Failed(FailureMessages.of(r.kind, r.message, r.retryAfterSeconds))
                    is StructuredResult.Success -> {
                        val rubric = GradeRules.score(r.value, p.rubric)
                        PracticalOutcome.Graded(rubric.passed, rubric.score, Trust.AI_GRADED, grade = r.value, rubricScore = rubric)
                    }
                }
            }
        }
        val feedback = when {
            outcome.report != null -> ModelJson.encodeToString(RunReport.serializer(), outcome.report)
            outcome.grade != null -> ModelJson.encodeToString(GradeResponse.serializer(), outcome.grade)
            else -> "{}"
        }
        learning.recordPracticalAttempt(stored.itemId, answer, outcome.score, outcome.passed, outcome.trust, feedback)
        learning.recordPracticalResult(
            place.chapter.id,
            PracticalResult(outcome.score, outcome.passed, outcome.trust, p.kind.wire, time.now()),
        )
        subjects.touch(place.subject.id)
        return outcome
    }
}
