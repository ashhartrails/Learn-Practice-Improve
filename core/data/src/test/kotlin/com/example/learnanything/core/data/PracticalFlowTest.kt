package com.example.learnanything.core.data

import com.example.learnanything.core.ai.ProviderRouter
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.validation.JsonSchemaValidator
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.RoomAiCacheStore
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.data.usecase.FindChapterUseCase
import com.example.learnanything.core.data.usecase.GetPracticalUseCase
import com.example.learnanything.core.data.usecase.PracticalLoad
import com.example.learnanything.core.data.usecase.PracticalOutcome
import com.example.learnanything.core.data.usecase.QuizPlace
import com.example.learnanything.core.data.usecase.SubmitPracticalUseCase
import com.example.learnanything.core.data.usecase.Trust
import com.example.learnanything.core.model.ChapterGate
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.NextStep
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import com.example.learnanything.core.model.practical.CodeRunner
import com.example.learnanything.core.model.practical.PracticalKind
import com.example.learnanything.core.model.practical.PracticalResponse
import com.example.learnanything.core.model.practical.RunReport
import com.example.learnanything.core.model.practical.TestOutcome
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Practical generation (with generate-then-validate), grading and the chapter gate, on a real Room DB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PracticalFlowTest {
    /** Passes a test when the code contains "return a + b" (the right answer). */
    private class FakeRunner : CodeRunner {
        val runs = mutableListOf<String>()
        override suspend fun run(practical: PracticalResponse, code: String): RunReport {
            runs += code
            val ok = "return a + b" in code
            return RunReport(practical.tests.map { TestOutcome(it.name, ok, if (ok) "" else "Assertion failed") })
        }
    }

    private lateinit var db: AppDatabase
    private val clock = TestClock()
    private lateinit var subjects: RoomSubjectRepository
    private lateinit var learning: LearningRepository
    private val runner = FakeRunner()
    private val replies = ArrayDeque<String>()
    private lateinit var ai: FakeAi
    private lateinit var caller: StructuredCaller
    private lateinit var chapterId: String
    private lateinit var subjectId: String

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        subjects = RoomSubjectRepository(db, clock)
        learning = LearningRepository(db, clock)
        ai = FakeAi(mapOf(CallType.PRACTICAL to { replies.removeFirst() }, CallType.GRADE to { replies.removeFirst() }))
        caller = StructuredCaller(ProviderRouter(listOf(ai)), JsonSchemaValidator(), RoomAiCacheStore(db.aiCacheDao(), clock), Json { ignoreUnknownKeys = true })
        subjectId = subjects.saveOutline(
            OutlineRequest("Python", Level.BEGINNER, 3),
            OutlineResponse("Python basics", "Learn Python.", listOf(OutlineChapter("Functions", "Write functions", listOf(
                OutlineTopic("Defining functions", listOf("Write one"), emptyList(), 10),
            )))),
        )
        chapterId = subjects.outline(subjectId)!!.chapters[0].id
    }

    @After fun tearDown() = db.close()

    private fun get() = GetPracticalUseCase(FindChapterUseCase(subjects), caller, learning, runner, clock)
    private fun submit() = SubmitPracticalUseCase(runner, caller, learning, subjects, clock)
    private suspend fun place(): QuizPlace = FindChapterUseCase(subjects)(chapterId)!!

    private fun python(reference: String) = """
        {"kind":"python","title":"Add two numbers","task":"Write add(a, b) that returns the sum of a and b.",
         "starterCode":"def add(a, b):\n    pass",
         "tests":[{"name":"small","code":"assert add(2, 3) == 5"},{"name":"negative","code":"assert add(-1, -1) == -2"},{"name":"zero","code":"assert add(0, 0) == 0"}],
         "referenceSolution":"$reference","sqlSetup":"","ordered":false,"rubric":[],"modelAnswer":""}
    """

    private val written = """
        {"kind":"written","title":"Explain functions","task":"Explain why functions help you reuse code, with an example.",
         "starterCode":"","tests":[],"referenceSolution":"","sqlSetup":"","ordered":false,
         "rubric":[{"criterion":"Says functions avoid repeating code","points":2},{"criterion":"Gives an example","points":2},{"criterion":"Mentions parameters","points":1}],
         "modelAnswer":"Functions let you write code once and call it many times, for example add(a, b)."}
    """

    @Test fun codeTaskIsVerifiedThenStoredAndGradedByItsTests() = runTest {
        replies += python("def add(a, b):\n    return a + b")
        val load = get()(chapterId) as PracticalLoad.Ready
        assertThat(load.stored.practical.kind).isEqualTo(PracticalKind.PYTHON)
        // Verification ran the reference (passes) and the starter (fails).
        assertThat(runner.runs).hasSize(2)

        // Opening again reads Room, no new AI call.
        get()(chapterId) as PracticalLoad.Ready
        assertThat(ai.calls).hasSize(1)

        // The gate: a wrong answer fails, a right one passes.
        val wrong = submit()(place(), load.stored, "def add(a, b):\n    return a - b") as PracticalOutcome.Graded
        assertThat(wrong.passed).isFalse()
        assertThat(wrong.trust).isEqualTo(Trust.EXACT)
        assertThat(subjects.outline(subjectId)!!.chapters[0].practicalPassed).isFalse()

        val right = submit()(place(), load.stored, "def add(a, b):\n    return a + b") as PracticalOutcome.Graded
        assertThat(right.passed).isTrue()
        assertThat(right.score).isEqualTo(1.0)
        assertThat(subjects.outline(subjectId)!!.chapters[0].practicalPassed).isTrue()
        assertThat(learning.lastPracticalAnswer(load.stored.itemId)).contains("return a + b")

        // A later fail never replaces the pass.
        submit()(place(), load.stored, "oops")
        assertThat(subjects.outline(subjectId)!!.chapters[0].practicalPassed).isTrue()
    }

    @Test fun taskWhoseReferenceFailsItsOwnTestsIsDiscardedAndRegenerated() = runTest {
        replies += python("def add(a, b):\n    return a * b")
        replies += python("def add(a, b):\n    return a + b")
        val load = get()(chapterId) as PracticalLoad.Ready
        assertThat(ai.calls).hasSize(2)
        assertThat(ai.calls[1].prompt).contains("reference solution did not pass")
        assertThat(load.stored.practical.referenceSolution).contains("a + b")
    }

    @Test fun twoBadTasksInARowFailWithAPlainMessage() = runTest {
        replies += python("def add(a, b):\n    return a * b")
        replies += python("def add(a, b):\n    return 0")
        val load = get()(chapterId)
        assertThat(load).isInstanceOf(PracticalLoad.Failed::class.java)
        assertThat(learning.practical(chapterId)).isNull()
    }

    @Test fun askingForAnotherKindPassesItToTheAiAndEnforcesIt() = runTest {
        replies += python("def add(a, b):\n    return a + b") // wrong kind: rejected by the rules, retried
        replies += written
        val load = get()(chapterId, kind = PracticalKind.WRITTEN) as PracticalLoad.Ready
        assertThat(load.stored.practical.kind).isEqualTo(PracticalKind.WRITTEN)
        assertThat(ai.calls.first().prompt).contains("asked for kind \"written\"")
        assertThat(ai.calls[1].prompt).contains("kind must be")
    }

    @Test fun numericTaskIsCheckedWithTolerance() = runTest {
        replies += """
            {"kind":"numeric","title":"Speed","task":"A car goes 150 km in 2 hours. What is its average speed?",
             "starterCode":"","tests":[],"referenceSolution":"","sqlSetup":"","ordered":false,
             "number":75,"tolerance":0.5,"unit":"km/h","rubric":[],"modelAnswer":""}
        """
        val load = get()(chapterId) as PracticalLoad.Ready
        assertThat(runner.runs).isEmpty()
        assertThat((submit()(place(), load.stored, "70 km/h") as PracticalOutcome.Graded).passed).isFalse()
        val right = submit()(place(), load.stored, "75.3") as PracticalOutcome.Graded
        assertThat(right.passed).isTrue()
        assertThat(right.expected).contains("75")
    }

    @Test fun writtenAnswerIsRubricGradedAndQuotesMustBeReal() = runTest {
        replies += written
        val load = get()(chapterId) as PracticalLoad.Ready
        val answer = "Functions mean you don't repeat code. For example def greet(name) takes a parameter."
        // First grade quotes text that isn't in the answer: rejected and retried.
        replies += """{"criteria":[{"index":0,"met":true,"evidence":"functions are reusable","feedback":"Good."},
            {"index":1,"met":true,"evidence":"def greet(name)","feedback":"Nice example."},
            {"index":2,"met":true,"evidence":"takes a parameter","feedback":"Yes."}],"summary":"Well done overall."}"""
        replies += """{"criteria":[{"index":0,"met":true,"evidence":"you don't repeat code","feedback":"Good."},
            {"index":1,"met":true,"evidence":"def greet(name)","feedback":"Nice example."},
            {"index":2,"met":false,"evidence":"","feedback":"Say what parameters do."}],"summary":"Well done overall."}"""
        val graded = submit()(place(), load.stored, answer) as PracticalOutcome.Graded
        assertThat(ai.calls.count { it.callType == CallType.GRADE }).isEqualTo(2)
        assertThat(ai.calls.last().prompt).contains("word for word")
        assertThat(graded.trust).isEqualTo(Trust.AI_GRADED)
        assertThat(graded.rubricScore!!.earned).isEqualTo(4)
        assertThat(graded.passed).isTrue() // 4 of 5 = 80%
    }

    @Test fun chapterNeedsQuizAndPracticalToFinish() = runTest {
        val topic = subjects.outline(subjectId)!!.chapters[0].topics[0]
        learning.completeTopic(topic.id)
        learning.recordGate(chapterId, ChapterGate(0.9, true, 1, 1, requiresPractical = true))
        assertThat(NextStep.of(subjects.outline(subjectId)!!)).isEqualTo(NextStep.DoPractical(chapterId, "Functions"))

        replies += python("def add(a, b):\n    return a + b")
        val load = get()(chapterId) as PracticalLoad.Ready
        submit()(place(), load.stored, "def add(a, b):\n    return a + b")
        assertThat(subjects.outline(subjectId)!!.chapters[0].isFinished).isTrue()
        assertThat(NextStep.of(subjects.outline(subjectId)!!)).isEqualTo(NextStep.CourseComplete)
    }
}
