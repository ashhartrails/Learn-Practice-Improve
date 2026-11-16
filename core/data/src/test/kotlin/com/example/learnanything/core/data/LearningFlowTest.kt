package com.example.learnanything.core.data

import com.example.learnanything.core.ai.ProviderRouter
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.validation.JsonSchemaValidator
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.RoomAiCacheStore
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.data.repository.Streak
import com.example.learnanything.core.data.usecase.FindChapterUseCase
import com.example.learnanything.core.data.usecase.FindTopicUseCase
import com.example.learnanything.core.data.usecase.FinishTopicUseCase
import com.example.learnanything.core.data.usecase.GetQuizUseCase
import com.example.learnanything.core.data.usecase.GradeAnswerUseCase
import com.example.learnanything.core.data.usecase.LessonResult
import com.example.learnanything.core.data.usecase.OpenLessonUseCase
import com.example.learnanything.core.data.usecase.QuizResult
import com.example.learnanything.core.data.usecase.ReviewAnswerUseCase
import com.example.learnanything.core.data.usecase.SubmitQuizUseCase
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.AnswerChecker
import com.example.learnanything.core.model.question.Question
import com.google.common.truth.Truth.assertThat
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The M1 loop against a real Room database and a fake AI: lesson → checks → quiz → gate → reviews. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LearningFlowTest {
    private lateinit var db: AppDatabase
    private val clock = TestClock()
    private lateinit var subjects: RoomSubjectRepository
    private lateinit var learning: LearningRepository
    private lateinit var ai: FakeAi
    private lateinit var caller: StructuredCaller
    private lateinit var subjectId: String

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        subjects = RoomSubjectRepository(db, clock)
        learning = LearningRepository(db, clock)
        ai = FakeAi(mapOf(CallType.LESSON to { Fixtures.LESSON }, CallType.QUIZ to { Fixtures.quiz(2) }))
        caller = StructuredCaller(ProviderRouter(listOf(ai)), JsonSchemaValidator(), RoomAiCacheStore(db.aiCacheDao(), clock), Json { ignoreUnknownKeys = true })
        subjectId = subjects.saveOutline(
            OutlineRequest("SQL", Level.BEGINNER, 3),
            OutlineResponse("SQL joins", "Learn joins.", listOf(OutlineChapter("Basics", "Write joins", listOf(
                OutlineTopic("Inner joins", listOf("Write one"), emptyList(), 10),
                OutlineTopic("Left joins", listOf("Write one"), emptyList(), 10),
            )))),
        )
    }

    @After fun tearDown() = db.close()

    private fun openLesson() = OpenLessonUseCase(FindTopicUseCase(subjects), caller, learning, subjects)
    private fun correctAnswer(q: Question): Answer = when (q) {
        is Question.SingleChoice -> Answer.Choice(setOf(q.correctIndex))
        is Question.MultiChoice -> Answer.Choice(q.correct)
        is Question.Numeric -> Answer.Number(q.value.toString())
        is Question.ShortText -> Answer.Text(q.accepted.first())
    }

    @Test fun lessonIsGeneratedOnceThenReadFromRoom() = runTest {
        val topicId = subjects.outline(subjectId)!!.chapters[0].topics[0].id
        val first = openLesson()(topicId) as LessonResult.Ready
        assertThat(first.lesson.checks).hasSize(2)
        assertThat(first.lesson.tryIt.question).isInstanceOf(Question.SingleChoice::class.java)
        assertThat(ai.calls.single().prompt).contains("Topic: Inner joins")

        val again = openLesson()(topicId) as LessonResult.Ready
        assertThat(ai.calls).hasSize(1)
        assertThat(again.lesson.checks.map { it.id }).isEqualTo(first.lesson.checks.map { it.id })
    }

    @Test fun fullChapterLoopPassesTheGateAndSchedulesReviews() = runTest {
        val grade = GradeAnswerUseCase(learning)
        val topics = subjects.outline(subjectId)!!.chapters[0].topics
        for (topic in topics) {
            val lesson = (openLesson()(topic.id) as LessonResult.Ready).lesson
            val results = lesson.checks.map { grade(it, correctAnswer(it.question), com.example.learnanything.core.data.repository.AttemptContext.LESSON).correct }
            FinishTopicUseCase(learning)(topic, results)
        }
        val chapter = subjects.outline(subjectId)!!.chapters[0]
        assertThat(chapter.topicsDone).isTrue()

        val quiz = GetQuizUseCase(FindChapterUseCase(subjects), caller, learning)(chapter.id) as QuizResult.Ready
        assertThat(quiz.items).hasSize(8)
        // 7 of 8 right = 87.5%: a pass.
        val answers = quiz.items.mapIndexed { i, item -> item.id to if (i == 0) Answer.Text("wrong") else correctAnswer(item.question) }.toMap()
        val outcome = SubmitQuizUseCase(grade, learning, subjects, clock)(quiz.place, quiz.items, answers)
        assertThat(outcome.score).isWithin(1e-9).of(0.875)
        assertThat(outcome.passed).isTrue()
        // M2: the practical is still to do.
        val afterQuiz = subjects.outline(subjectId)!!
        assertThat(afterQuiz.chapters[0].isFinished).isFalse()
        assertThat(com.example.learnanything.core.model.NextStep.of(afterQuiz))
            .isInstanceOf(com.example.learnanything.core.model.NextStep.DoPractical::class.java)

        val progress = learning.observeProgress().first()
        assertThat(progress.topicsCompleted).isEqualTo(2)
        assertThat(progress.chaptersPassed).isEqualTo(0)
        assertThat(progress.reviewsDue).isEqualTo(0)
        assertThat(progress.averageMastery!!).isGreaterThan(0.5)

        // A week later both topics are due; one review item each.
        clock.now += 8 * DAY
        val due = learning.dueReviews()
        assertThat(due.map { it.topicTitle }).containsExactly("Inner joins", "Left joins")
        ReviewAnswerUseCase(grade, learning)(due[0].item, due[0].topicTitle, Answer.Text("nope"))
        assertThat(learning.dueReviews().map { it.topicTitle }).containsExactly("Left joins")
    }

    @Test fun failedRetakeNeverReplacesAPass() = runTest {
        val grade = GradeAnswerUseCase(learning)
        val chapterId = subjects.outline(subjectId)!!.chapters[0].id
        val getQuiz = GetQuizUseCase(FindChapterUseCase(subjects), caller, learning)
        val quiz = getQuiz(chapterId) as QuizResult.Ready
        SubmitQuizUseCase(grade, learning, subjects, clock)(quiz.place, quiz.items, quiz.items.associate { it.id to correctAnswer(it.question) })

        val retake = getQuiz(chapterId, newQuestions = true) as QuizResult.Ready
        assertThat(ai.calls.count { it.callType == CallType.QUIZ }).isEqualTo(2)
        assertThat(retake.items.map { it.id }).containsNoneIn(quiz.items.map { it.id })
        val fail = SubmitQuizUseCase(grade, learning, subjects, clock)(retake.place, retake.items, emptyMap())
        assertThat(fail.passed).isFalse()
        assertThat(subjects.outline(subjectId)!!.chapters[0].gate!!.passed).isTrue()
    }

    @Test fun deletedCoursesDropOutOfReviewsAndProgress() = runTest {
        val topic = subjects.outline(subjectId)!!.chapters[0].topics[0]
        learning.applySession(topic.id, topic.title, listOf(false))
        clock.now += 2 * DAY
        assertThat(learning.dueReviews()).isEmpty() // no lesson items yet, so nothing to ask
        openLesson()(topic.id)
        assertThat(learning.dueReviews()).hasSize(1)
        subjects.delete(subjectId)
        assertThat(learning.dueReviews()).isEmpty()
        assertThat(learning.observeProgress().first().topicsTotal).isEqualTo(0)
    }

    @Test fun streakCountsConsecutiveDays() {
        val zone = ZoneOffset.UTC
        val today = 100 * DAY + 3_600_000
        assertThat(Streak.days(listOf(today, today - DAY, today - 2 * DAY), today, zone)).isEqualTo(3)
        assertThat(Streak.days(listOf(today - DAY, today - 2 * DAY), today, zone)).isEqualTo(2)
        assertThat(Streak.days(listOf(today - 3 * DAY), today, zone)).isEqualTo(0)
        assertThat(AnswerChecker.normalize(" Where ")).isEqualTo("where")
    }
}
