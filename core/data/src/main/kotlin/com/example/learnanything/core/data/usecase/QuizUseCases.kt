package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.CallProgress
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.StructuredResult
import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.repository.AttemptContext
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.StoredItem
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.Chapter
import com.example.learnanything.core.model.ChapterGate
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.GradeResult
import com.example.learnanything.core.model.quiz.QuizContext
import com.example.learnanything.core.model.quiz.QuizPrompt
import com.example.learnanything.core.model.quiz.QuizResponse
import com.example.learnanything.core.model.quiz.QuizRules
import com.example.learnanything.core.model.quiz.QuizTopic
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class QuizPlace(val subject: Subject, val chapter: Chapter)

sealed interface QuizResult {
    data class Ready(val place: QuizPlace, val items: List<StoredItem>) : QuizResult
    data class Failed(val message: UserMessage) : QuizResult
}

data class QuizOutcome(
    val score: Double,
    val passed: Boolean,
    val results: List<Pair<StoredItem, GradeResult>>,
)

class FindChapterUseCase @Inject constructor(private val subjects: SubjectRepository) {
    suspend operator fun invoke(chapterId: String): QuizPlace? {
        for (subject in subjects.observeSubjects().first()) {
            val outline = subjects.outline(subject.id) ?: continue
            outline.chapters.firstOrNull { it.id == chapterId }?.let { return QuizPlace(outline.subject, it) }
        }
        return null
    }
}

/** Returns the chapter's current quiz, generating one if needed or if [newQuestions] is true. */
class GetQuizUseCase @Inject constructor(
    private val findChapter: FindChapterUseCase,
    private val caller: StructuredCaller,
    private val learning: LearningRepository,
) {
    suspend operator fun invoke(chapterId: String, newQuestions: Boolean = false, onProgress: (CallProgress) -> Unit = {}): QuizResult {
        val place = findChapter(chapterId) ?: return QuizResult.Failed(UserMessage("This chapter no longer exists."))
        if (!newQuestions) {
            learning.quizItems(chapterId).takeIf { it.isNotEmpty() }?.let { return QuizResult.Ready(place, it) }
        }
        val topics = place.chapter.topics
        if (topics.isEmpty()) return QuizResult.Failed(UserMessage("This chapter has no topics."))
        val variant = learning.quizVariant(chapterId) + 1
        val spec = QuizPrompt.build(
            QuizContext(
                subjectTitle = place.subject.title,
                level = place.subject.level,
                chapterTitle = place.chapter.title,
                chapterGoal = place.chapter.goal,
                topics = topics.map { QuizTopic(it.title, it.objectives) },
                variant = variant,
            ),
        )
        val rules: (QuizResponse) -> List<String> = { QuizRules.check(it, topics.size) }
        return when (val r = caller.call(spec, QuizResponse.serializer(), rules, onProgress)) {
            is StructuredResult.Success -> {
                val questions = r.value.questions.map { topics[it.topicIndex].id to it.question }
                QuizResult.Ready(place, learning.saveQuiz(chapterId, questions, variant))
            }
            is StructuredResult.Failure -> QuizResult.Failed(FailureMessages.of(r.kind, r.message, r.retryAfterSeconds))
        }
    }
}

/** Grades the whole quiz by code, updates mastery per topic, and records the chapter gate. */
class SubmitQuizUseCase @Inject constructor(
    private val grade: GradeAnswerUseCase,
    private val learning: LearningRepository,
    private val subjects: SubjectRepository,
    private val time: TimeSource,
) {
    suspend operator fun invoke(place: QuizPlace, items: List<StoredItem>, answers: Map<String, Answer>): QuizOutcome {
        val results = items.map { item ->
            val answer = answers[item.id] ?: Answer.Text("")
            item to grade(item, answer, AttemptContext.QUIZ)
        }
        val score = if (results.isEmpty()) 0.0 else results.count { it.second.correct }.toDouble() / results.size
        val passed = score >= QuizRules.PASS_MARK
        val titles = place.chapter.topics.associate { it.id to it.title }
        results.groupBy { it.first.topicId }.forEach { (topicId, rs) ->
            titles[topicId]?.let { learning.applySession(topicId, it, rs.map { r -> r.second.correct }) }
        }
        learning.recordGate(place.chapter.id, ChapterGate(score, passed, time.now(), learning.quizVariant(place.chapter.id)))
        subjects.touch(place.subject.id)
        return QuizOutcome(score, passed, results)
    }
}
