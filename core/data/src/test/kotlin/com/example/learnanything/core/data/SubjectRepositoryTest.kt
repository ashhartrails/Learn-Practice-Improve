package com.example.learnanything.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.RoomAiCacheStore
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubjectRepositoryTest {
    private lateinit var db: AppDatabase
    private var clock = 1_000L
    private val time = TimeSource { clock }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    @After fun tearDown() = db.close()

    private val outline = OutlineResponse(
        title = "SQL joins",
        summary = "Combine tables confidently.",
        chapters = listOf(
            OutlineChapter("Basics", "Write a simple join", listOf(
                OutlineTopic("Keys", listOf("Spot keys"), emptyList(), 10),
                OutlineTopic("Inner join", listOf("Write one"), listOf("Keys"), 15),
            )),
            OutlineChapter("Outer joins", "Use LEFT JOIN", listOf(
                OutlineTopic("Left join", listOf("Write one"), emptyList(), 15),
            )),
        ),
    )

    @Test fun savedOutlineReadsBackInOrder() = runTest {
        val repo = RoomSubjectRepository(db, time)
        val id = repo.saveOutline(OutlineRequest("Learn SQL joins", Level.BEGINNER, 3), outline)

        val saved = repo.observeOutline(id).first()!!
        assertThat(saved.subject.title).isEqualTo("SQL joins")
        assertThat(saved.subject.level).isEqualTo(Level.BEGINNER)
        assertThat(saved.chapters.map { it.title }).containsExactly("Basics", "Outer joins").inOrder()
        assertThat(saved.chapters[0].topics.map { it.title }).containsExactly("Keys", "Inner join").inOrder()
        assertThat(saved.chapters[0].topics[1].prerequisites).containsExactly("Keys")
        assertThat(repo.observeSubjects().first()).hasSize(1)
    }

    @Test fun deleteLeavesATombstoneAndHidesTheSubject() = runTest {
        val repo = RoomSubjectRepository(db, time)
        val id = repo.saveOutline(OutlineRequest("SQL", Level.BEGINNER, 3), outline)
        clock = 2_000L
        repo.delete(id)

        assertThat(repo.observeSubjects().first()).isEmpty()
        assertThat(repo.observeOutline(id).first()).isNull()
    }

    @Test fun aiCacheRoundTrips() = runTest {
        val cache = RoomAiCacheStore(db.aiCacheDao(), time)
        assertThat(cache.get("h")).isNull()
        cache.put("h", CallType.OUTLINE, "{}")
        assertThat(cache.get("h")).isEqualTo("{}")
    }
}
