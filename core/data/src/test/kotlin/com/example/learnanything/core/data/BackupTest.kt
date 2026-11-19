package com.example.learnanything.core.data

import androidx.test.core.app.ApplicationProvider
import com.example.learnanything.core.data.backup.BackupContents
import com.example.learnanything.core.data.backup.BackupFormatException
import com.example.learnanything.core.data.backup.BackupOutcome
import com.example.learnanything.core.data.backup.BackupPrefs
import com.example.learnanything.core.data.backup.BackupRepository
import com.example.learnanything.core.data.backup.BackupStore
import com.example.learnanything.core.data.backup.BackupZip
import com.example.learnanything.core.data.backup.DriveAuth
import com.example.learnanything.core.data.backup.DriveClient
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.EnrichmentRepository
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.RoomAiCacheStore
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.ModelJson
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.backup.BackupManifest
import com.example.learnanything.core.model.backup.RestoreMode
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Backup and restore against real Room databases (two "phones") and a mock Drive server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {
    private val clock = TestClock()
    private lateinit var phoneA: AppDatabase
    private lateinit var phoneB: AppDatabase
    private val server = MockWebServer()

    @Before fun setUp() {
        phoneA = inMemoryDb()
        phoneB = inMemoryDb()
        server.start()
    }

    @After fun tearDown() {
        phoneA.close()
        phoneB.close()
        server.close()
    }

    private suspend fun seed(db: AppDatabase, title: String = "SQL joins"): String {
        val subjects = RoomSubjectRepository(db, clock)
        val id = subjects.saveOutline(
            OutlineRequest("SQL", Level.BEGINNER, 3),
            OutlineResponse(title, "Learn joins.", listOf(OutlineChapter("Basics", "Write joins", listOf(
                OutlineTopic("Inner joins", listOf("Write one"), emptyList(), 10),
                OutlineTopic("Left joins", listOf("Write one"), emptyList(), 10),
            )))),
        )
        val topic = subjects.outline(id)!!.chapters[0].topics[0]
        LearningRepository(db, clock).applySession(topic.id, topic.title, listOf(true))
        LearningRepository(db, clock).completeTopic(topic.id)
        EnrichmentRepository(db, clock).saveProfile("Developer", "SQL")
        RoomAiCacheStore(db.aiCacheDao(), clock).put("hash", CallType.LESSON, "{}")
        return id
    }


    @Test fun replaceRestoreCopiesEverythingExceptTheAiCache() = runTest {
        val subjectId = seed(phoneA)
        val contents = BackupContents(
            BackupManifest(dbVersion = 4, createdAt = 1, appVersion = "t", tables = emptyMap()),
            BackupStore(phoneA).dump(),
        )
        val bytes = BackupZip.toBytes(contents)
        val read = BackupZip.read(bytes.inputStream())
        assertThat(read.tables.keys).doesNotContain("ai_cache")

        BackupStore(phoneB).write(read.tables, RestoreMode.REPLACE)
        val restored = RoomSubjectRepository(phoneB, clock).outline(subjectId)!!
        assertThat(restored.subject.title).isEqualTo("SQL joins")
        assertThat(restored.completedTopicCount).isEqualTo(1)
        assertThat(LearningRepository(phoneB, clock).observeProgress().first().averageMastery).isNotNull()
        assertThat(EnrichmentRepository(phoneB, clock).profile().background).isEqualTo("Developer")
        assertThat(phoneB.aiCacheDao().get("hash")).isNull()
    }

    @Test fun replaceRemovesCoursesThatAreNotInTheBackup() = runTest {
        val keep = seed(phoneA)
        val local = seed(phoneB, "Only on B")
        BackupStore(phoneB).write(BackupStore(phoneA).dump(), RestoreMode.REPLACE)
        assertThat(RoomSubjectRepository(phoneB, clock).outline(local)).isNull()
        assertThat(RoomSubjectRepository(phoneB, clock).outline(keep)).isNotNull()
    }

    @Test fun mergeKeepsBothPhonesAndNewestWins() = runTest {
        val shared = seed(phoneA)
        BackupStore(phoneB).write(BackupStore(phoneA).dump(), RestoreMode.REPLACE)
        val onlyB = seed(phoneB, "Only on B")

        // A renames a chapter later; B finishes a topic later. Both changes must survive a merge into B.
        val a = RoomSubjectRepository(phoneA, clock)
        val chapterId = a.outline(shared)!!.chapters[0].id
        clock.now += 1_000
        a.renameChapter(chapterId, "Renamed on A")
        val b = RoomSubjectRepository(phoneB, clock)
        val leftJoin = b.outline(shared)!!.chapters[0].topics[1].id
        clock.now += 1_000
        LearningRepository(phoneB, clock).completeTopic(leftJoin)

        val written = BackupStore(phoneB).write(BackupStore(phoneA).dump(), RestoreMode.MERGE)
        assertThat(written).isGreaterThan(0)
        val merged = b.outline(shared)!!
        assertThat(merged.chapters[0].title).isEqualTo("Renamed on A")
        assertThat(merged.chapters[0].topics[1].isComplete).isTrue()
        assertThat(b.outline(onlyB)).isNotNull()
        // Upserts don't cascade: the chapter update kept its topics.
        assertThat(merged.chapters[0].topics).hasSize(2)
    }

    @Test fun mergeHonoursTombstones() = runTest {
        val id = seed(phoneA)
        BackupStore(phoneB).write(BackupStore(phoneA).dump(), RestoreMode.REPLACE)
        clock.now += 1_000
        RoomSubjectRepository(phoneA, clock).delete(id)
        BackupStore(phoneB).write(BackupStore(phoneA).dump(), RestoreMode.MERGE)
        assertThat(RoomSubjectRepository(phoneB, clock).outline(id)).isNull()
    }

    @Test fun zipWithoutManifestOrFromTheFutureIsRefused() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("tables/subject.json")); it.write("[]".toByteArray()); it.closeEntry() }
        assertThrows(BackupFormatException::class.java) { BackupZip.read(out.toByteArray().inputStream()) }

        val future = BackupZip.toBytes(BackupContents(BackupManifest(format = 99, dbVersion = 4, createdAt = 1, appVersion = "t", tables = emptyMap()), emptyMap()))
        assertThrows(BackupFormatException::class.java) { BackupZip.read(future.inputStream()) }
    }

    private class FakeAuth(var token: String?) : DriveAuth {
        override suspend fun accessToken() = token
    }

    private fun repo(auth: DriveAuth, prefs: BackupPrefs) = BackupRepository(
        ApplicationProvider.getApplicationContext(),
        BackupStore(phoneA),
        DriveClient(OkHttpClient(), Json { ignoreUnknownKeys = true }, server.url("/")),
        auth,
        prefs,
        clock,
    )

    private fun prefs(name: String) = BackupPrefs(
        PreferenceDataStoreFactory.create {
            java.io.File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "$name.preferences_pb").apply { delete() }
        },
    )

    @Test fun backupUploadsToTheAppFolderAndKeepsTheNewestFive() = runTest {
        seed(phoneA)
        val prefs = prefs("backup-a")
        prefs.enable("me@example.com")
        server.enqueue(MockResponse.Builder().code(200).body("""{"id":"new","name":"learn-backup-x.zip","createdTime":"2026-11-19T10:00:00Z","size":"100"}""").build())
        val files = (1..7).joinToString(",") { """{"id":"f$it","name":"learn-backup-$it.zip","createdTime":"2026-11-1${it}T10:00:00Z"}""" }
        server.enqueue(MockResponse.Builder().code(200).body("""{"files":[$files]}""").build())
        server.enqueue(MockResponse.Builder().code(204).build())
        server.enqueue(MockResponse.Builder().code(204).build())

        val outcome = repo(FakeAuth("TOKEN"), prefs).backupNow()
        assertThat(outcome).isInstanceOf(BackupOutcome.Done::class.java)

        val upload = server.takeRequest()
        assertThat(upload.url.encodedPath).isEqualTo("/upload/drive/v3/files")
        assertThat(upload.headers["Authorization"]).isEqualTo("Bearer TOKEN")
        val body = upload.body!!.utf8()
        assertThat(body).contains("\"parents\":[\"appDataFolder\"]")
        assertThat(body).contains("manifest.json")
        assertThat(server.takeRequest().url.queryParameter("spaces")).isEqualTo("appDataFolder")
        // The two oldest (f1, f2) are deleted.
        val deleted = listOf(server.takeRequest(), server.takeRequest()).map { it.url.encodedPath }
        assertThat(deleted).containsExactly("/drive/v3/files/f1", "/drive/v3/files/f2")
        assertThat(prefs.status.first().lastBackupAt).isEqualTo(clock.now)
    }

    @Test fun withoutAccessBackupAsksForSignIn() = runTest {
        val prefs = prefs("backup-b")
        assertThat(repo(FakeAuth("TOKEN"), prefs).backupNow()).isEqualTo(BackupOutcome.NeedsAccess)
        prefs.enable(null)
        assertThat(repo(FakeAuth(null), prefs).backupNow()).isEqualTo(BackupOutcome.NeedsAccess)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun backupFromANewerAppVersionIsRefused() = runTest {
        val newer = BackupContents(BackupManifest(dbVersion = 99, createdAt = 1, appVersion = "t", tables = emptyMap()), emptyMap())
        val outcome = repo(FakeAuth("T"), prefs("backup-c")).apply(newer, RestoreMode.REPLACE)
        assertThat(outcome).isInstanceOf(BackupOutcome.Failed::class.java)
    }
}
