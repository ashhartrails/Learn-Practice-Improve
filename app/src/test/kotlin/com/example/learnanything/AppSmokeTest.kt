package com.example.learnanything

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Launches the real app (Hilt graph, Room, DataStore, navigation) on Robolectric and
 * walks the M0 screens. Screenshots go to app/build/smoke-screens/ for a visual check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Room and DataStore emit on background threads, which the test clock doesn't track. */
    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun walksHomeCreateLibraryAndSettings() {
        waitForText("What do you want to learn?")
        shot("1-home-empty")

        compose.onNodeWithText("New subject", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Build my course").assertExists()
        shot("2-new-subject")

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Library").performClick()
        waitForText("Your library is empty")

        compose.onNodeWithText("You").performClick()
        waitForText("Gemini API key")
        compose.onNodeWithText("Save and test").assertExists()
        shot("3-settings")
    }

    @Test fun savedOutlineOpensAsSubjectMap() {
        // A second connection to the app's real database file, so the app reads what we write.
        val db = Room.databaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>(), AppDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()
        val repo = RoomSubjectRepository(db) { System.currentTimeMillis() }
        runBlocking {
            repo.saveOutline(
                OutlineRequest("Learn SQL joins", Level.BEGINNER, 3),
                OutlineResponse(
                    title = "SQL joins from scratch",
                    summary = "Combine tables confidently with inner, outer and self joins.",
                    chapters = listOf(
                        OutlineChapter("Relational basics", "Explain keys and write a two-table inner join.", listOf(
                            OutlineTopic("Tables and keys", listOf("Identify primary and foreign keys"), emptyList(), 10),
                            OutlineTopic("Inner joins", listOf("Write an INNER JOIN", "Predict the row count"), listOf("Tables and keys"), 15),
                        )),
                        OutlineChapter("Outer joins", "Keep unmatched rows with LEFT and FULL joins.", listOf(
                            OutlineTopic("Left joins", listOf("Write a LEFT JOIN"), listOf("Inner joins"), 15),
                        )),
                    ),
                ),
            )
        }
        db.close()
        // Library starts a fresh query, so it sees the row written above.
        compose.onNodeWithText("Library").performClick()
        waitForText("SQL joins from scratch")
        shot("4-library-with-subject")

        compose.onNodeWithText("SQL joins from scratch").performClick()
        waitForText("Relational basics")
        shot("5-subject-map")

        compose.onNodeWithText("Inner joins").performClick()
        waitForText("You will be able to")
        shot("6-topic-sheet")
    }
}
