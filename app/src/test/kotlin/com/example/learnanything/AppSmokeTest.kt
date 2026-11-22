package com.example.learnanything

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.ModelJson
import com.example.learnanything.core.model.lesson.LessonResponse
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import com.example.learnanything.core.ai.PromptHasher
import com.example.learnanything.core.data.repository.RoomAiCacheStore
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.rescue.RescueContext
import com.example.learnanything.core.model.rescue.RescuePrompt
import com.example.learnanything.core.model.rescue.RescueRung
import com.example.learnanything.core.model.resources.ResourcesContext
import com.example.learnanything.core.model.resources.ResourcesPrompt
import com.example.learnanything.core.model.practical.PracticalKind
import com.example.learnanything.core.model.practical.PracticalResponse
import com.example.learnanything.core.model.question.QuestionDto
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Launches the real app (Hilt graph, Room, DataStore, navigation) on Robolectric.
 * Lessons and the quiz are seeded into the real database first, so no AI call is made,
 * then the test walks the loop (lesson, quiz, practical) through the UI. Screenshots go to app/build/smoke-screens/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule val compose: ComposeTestRule = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun tearDown() { scenario?.close() }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Room and DataStore emit on background threads, which the test clock doesn't track. */
    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun click(text: String, substring: Boolean = false) =
        compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).onFirst().performClick()

    private fun nth(text: String, index: Int) =
        compose.onAllNodesWithText(text, useUnmergedTree = true)[index]

    private fun waitForCount(text: String, count: Int) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size >= count
        }
    }

    /** Lesson list positions: 0 chapter, 1 why, 2 cards, 3 example, 4 try it, 5 heading, 6+ checks. */
    private fun scrollToItem(index: Int) {
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToIndex(index)
        compose.waitForIdle()
    }

    @Test fun emptyAppShowsHomeCreateAndSettings() {
        launch()
        waitForText("What do you want to learn?")
        shot("01-home-empty")
        click("New subject")
        waitForText("Next")
        shot("02-new-subject")
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("You")
        waitForText("Day streak")
        shot("03-you")
    }

    @Test fun walksAChapterFromDraftToPassedQuizAndPractical() {
        seedCourse()
        launch()

        // Draft course: Home lists it; opening it goes to the outline editor.
        waitForText("SQL joins from scratch")
        click("SQL joins from scratch")
        waitForText("Start course")
        shot("04-outline-editor")
        click("Start course")

        // Subject map with Continue.
        waitForText("Continue: Inner joins")
        shot("05-subject-map")
        click("Continue: Inner joins")

        // Lesson (seeded, so no AI call).
        waitForText("Why it matters")
        shot("06-lesson-top")
        // Try it (practice): INNER is right.
        scrollToItem(4)
        nth("INNER", 0).performClick()
        compose.onAllNodesWithText("Check").onFirst().performClick()
        waitForCount("Correct", 1)
        // Check 1: LEFT is right. Options repeat across questions, so pick by position.
        scrollToItem(6)
        nth("LEFT", 1).performClick()
        compose.onAllNodesWithText("Check").onFirst().performClick()
        waitForCount("Correct", 2)
        shot("07-lesson-checked")
        // Check 2: answer wrong on purpose to see the feedback.
        scrollToItem(7)
        nth("RIGHT", 2).performClick()
        compose.onAllNodesWithText("Check").onFirst().performClick()
        waitForText("Not quite")
        shot("08-lesson-feedback")
        click("Finish topic")

        // Back on the map: topic done, quiz is next.
        waitForText("Take the chapter quiz")
        shot("09-map-quiz-ready")
        click("Take the chapter quiz")

        // Quiz: answer all 6 correctly and pass.
        waitForText("Question 1 of 6")
        repeat(6) { i ->
            click(if (i == 5) "INNER" else "LEFT")
            if (i == 0) shot("10-quiz-question")
            click(if (i == 5) "Submit" else "Next", substring = true)
        }
        waitForText("Passed! Next: the chapter practical.")
        shot("11-quiz-result")
        click("Back to course")
        waitForText("Chapter quiz: passed", substring = true)

        // Practical (seeded numeric task, checked by code): wrong fails, right passes.
        click("Do the practical")
        waitForText("Count the joined rows")
        shot("12-practical")
        compose.onNodeWithTag("practical-answer").performTextReplacement("5")
        click("Check answer")
        waitForText("Not yet")
        shot("13-practical-wrong")
        compose.onNodeWithTag("practical-answer").performTextReplacement("4")
        click("Check answer")
        waitForText("Passed. Chapter complete!")
        shot("14-practical-passed")
        click("Back to course")
        waitForText("Practical: passed")
        waitForText("Finished")

        // You tab shows the progress.
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        click("You")
        waitForText("1/1")
        shot("15-progress")
    }

    @Test fun rescueLadderHelpsFromTheLesson() {
        val seeded = seedCourse()
        seedRescueCache(seeded)
        launch()
        waitForText("SQL joins from scratch")
        click("SQL joins from scratch")
        waitForText("Start course")
        click("Start course")
        waitForText("Continue: Inner joins")
        click("Continue: Inner joins")
        waitForText("Why it matters")

        // Lesson list: 0 chapter, 1 why, 2 cards, 3 "I don't get it".
        scrollToItem(3)
        click("I don't get it")
        waitForText("Step 1 of 8: Simpler words")
        waitForText("Joins, simply")
        shot("16-rescue-simpler")

        // The check question decides: the lesson's first check (LEFT is right).
        scrollToItem(3)
        click("LEFT")
        compose.onAllNodesWithText("Check").onFirst().performClick()
        waitForText("It clicked", substring = true)
        shot("17-rescue-helped")
        click("Back to the lesson")
        // Back on the lesson (scrolled where it was).
        waitForText("I don't get it")

        // "Watch a short video" without a YouTube key offers a search chip.
        scrollToItem(1)
        click("Watch a short video")
        waitForText("sql inner join explained")
        shot("18-video-search-chip")
    }

    /** Puts AI replies for the rescue and resources calls in the cache, so no AI is needed. */
    private fun seedRescueCache(seeded: Seeded) = runBlocking {
        val db = Room.databaseBuilder<AppDatabase>(context, AppDatabase.NAME).setDriver(AndroidSQLiteDriver()).build()
        val cache = RoomAiCacheStore(db.aiCacheDao()) { System.currentTimeMillis() }
        val simpler = RescuePrompt.build(
            RescueContext(
                subjectTitle = "SQL joins from scratch",
                level = Level.BEGINNER,
                topicTitle = "Inner joins",
                objectives = listOf("Write an INNER JOIN"),
                prerequisites = emptyList(),
                lessonSummary = seeded.lesson.cards.joinToString(" ") { "${it.heading}: ${it.body}" },
                background = "",
                knownSkills = "",
            ),
            RescueRung.SIMPLER,
        )
        val body = "Think of two lists: orders and customers. " + (1..40).joinToString(" ") { "word$it" }
        cache.put(PromptHasher.hash(simpler), CallType.RESCUE, """{"title":"Joins, simply","body":"$body","blanks":[],"questions":[],"prerequisites":[],"rubric":[]}""")
        val resources = ResourcesPrompt.build(ResourcesContext("SQL joins from scratch", "Inner joins", listOf("Write an INNER JOIN"), Level.BEGINNER.label))
        cache.put(PromptHasher.hash(resources), CallType.RESOURCES, """{"searchQueries":["sql inner join explained"],"phetKey":"","visualDescription":"Two circles."}""")
        db.close()
    }

    private data class Seeded(val lesson: LessonResponse)

    /** Writes a draft course with a stored lesson and quiz through a second Room connection. */
    private fun seedCourse(): Seeded = runBlocking {
        val db = Room.databaseBuilder<AppDatabase>(context, AppDatabase.NAME).setDriver(AndroidSQLiteDriver()).build()
        val subjects = RoomSubjectRepository(db) { System.currentTimeMillis() }
        val learning = LearningRepository(db) { System.currentTimeMillis() }
        val subjectId = subjects.saveOutline(
            OutlineRequest("Learn SQL joins", Level.BEGINNER, 3),
            OutlineResponse(
                "SQL joins from scratch", "Combine tables confidently with inner and outer joins.",
                listOf(OutlineChapter("Relational basics", "Write a two-table inner join.", listOf(
                    OutlineTopic("Inner joins", listOf("Write an INNER JOIN"), emptyList(), 15),
                ))),
            ),
            draft = true,
        )
        val outline = subjects.outline(subjectId)!!
        val topicId = outline.chapters[0].topics[0].id
        val leftJoin = QuestionDto("single_choice", "Which join keeps every row from the left table?", listOf("INNER", "LEFT", "RIGHT"), listOf(1), explanation = "LEFT JOIN keeps unmatched left rows, with NULLs on the right.")
        val inner = QuestionDto("single_choice", "Which join keeps only matching rows?", listOf("INNER", "LEFT", "RIGHT"), listOf(0), explanation = "INNER JOIN returns only rows that match on both sides.")
        val lesson = ModelJson.decodeFromString(
            LessonResponse.serializer(),
            """
            {"whyItMatters":"Most real questions need data from more than one table.",
             "cards":[
               {"heading":"What a join does","body":"A join combines rows from two tables when a **key** matches.\n\n```\nSELECT o.id, c.name\nFROM orders o\nJOIN customers c ON c.id = o.customer_id;\n```"},
               {"heading":"Inner joins","body":"- Keep only rows that match\n- Unmatched rows disappear"}],
             "workedExample":{"problem":"List each order with its customer's name.","steps":["Start from `orders`","Join `customers` on `customer_id`","Select the columns you need"],"answer":"`SELECT o.id, c.name FROM orders o JOIN customers c ON c.id = o.customer_id`"},
             "tryIt":${ModelJson.encodeToString(QuestionDto.serializer(), inner)},
             "checks":[${ModelJson.encodeToString(QuestionDto.serializer(), leftJoin)},${ModelJson.encodeToString(QuestionDto.serializer(), inner.copy(prompt = "Which join drops unmatched rows on both sides?"))}]}
            """.trimIndent(),
        )
        learning.saveLesson(topicId, lesson)
        val quiz = (0 until 6).map { i ->
            topicId to if (i == 5) inner else leftJoin.copy(prompt = "(${i + 1}) ${leftJoin.prompt}")
        }
        learning.saveQuiz(outline.chapters[0].id, quiz, variant = 1)
        learning.savePractical(
            outline.chapters[0].id, topicId,
            PracticalResponse(
                kind = PracticalKind.NUMERIC,
                title = "Count the joined rows",
                task = "`orders` has 5 rows; 4 of them have a matching customer. How many rows does an **INNER JOIN** return?",
                number = 4.0, tolerance = 0.0,
            ),
            variant = 1,
        )
        db.close()
        Seeded(lesson)
    }
}
