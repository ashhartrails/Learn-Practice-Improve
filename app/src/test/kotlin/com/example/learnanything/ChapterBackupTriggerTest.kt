package com.example.learnanything

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.example.learnanything.backup.ChapterBackupTrigger
import com.example.learnanything.core.data.backup.BackupPrefs
import com.example.learnanything.core.data.backup.BackupScheduler
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.model.ChapterGate
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChapterBackupTriggerTest {
    private class CountingScheduler : BackupScheduler {
        @Volatile var chapters = 0
        override fun chapterFinished() { chapters++ }
        override fun setDaily(enabled: Boolean) = Unit
    }

    @Test fun finishingAChapterSchedulesABackupOnlyWhenBackupIsOn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
        val prefs = BackupPrefs(PreferenceDataStoreFactory.create { File(context.filesDir, "trigger.preferences_pb").apply { delete() } })
        val scheduler = CountingScheduler()
        val learning = LearningRepository(db) { 1L }
        val subjects = RoomSubjectRepository(db) { 1L }
        val id = subjects.saveOutline(
            OutlineRequest("SQL", Level.BEGINNER, 3),
            OutlineResponse("SQL", "Learn.", listOf(
                OutlineChapter("One", "Goal one", listOf(OutlineTopic("A", listOf("x"), emptyList(), 10))),
                OutlineChapter("Two", "Goal two", listOf(OutlineTopic("B", listOf("x"), emptyList(), 10))),
            )),
        )
        val chapters = subjects.outline(id)!!.chapters
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ChapterBackupTrigger(learning, prefs, scheduler).start(scope)
        delay(500)

        // Backup off: finishing a chapter schedules nothing.
        learning.recordGate(chapters[0].id, ChapterGate(0.9, true, 1, 1))
        delay(500)
        assertThat(scheduler.chapters).isEqualTo(0)

        // Backup on: the next finished chapter schedules one.
        prefs.enable("me@example.com")
        learning.recordGate(chapters[1].id, ChapterGate(0.9, true, 1, 1))
        withTimeout(5_000) { while (scheduler.chapters == 0) delay(50) }
        assertThat(scheduler.chapters).isEqualTo(1)
        scope.cancel()
        db.close()
    }
}
