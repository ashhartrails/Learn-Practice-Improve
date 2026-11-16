package com.example.learnanything.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds a real version-1 database from the committed schema (schemas/.../1.json),
 * fills it with M0-style data, then opens it with the current AppDatabase so Room runs
 * the 1 -> 2 auto-migration and validates the result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Creates an empty database at [version] from the committed schema and returns an open connection. */
    private fun createAt(version: Int, name: String): Pair<File, androidx.sqlite.SQLiteConnection> {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        val schema = Json.parseToJsonElement(
            File("schemas/com.example.learnanything.core.data.db.AppDatabase/$version.json").readText(),
        ).jsonObject["database"]!!.jsonObject
        val connection = AndroidSQLiteDriver().open(file.path)
        schema["entities"]!!.jsonArray.forEach { entity ->
            val e = entity.jsonObject
            val table = e["tableName"]!!.jsonPrimitive.content
            connection.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach { index ->
                connection.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
        connection.execSQL("PRAGMA user_version = $version")
        return file to connection
    }

    @Test fun version1DataSurvivesMigrationToVersion2() = runBlocking {
        val (file, connection) = createAt(1, "migration-test.db")
        connection.execSQL("INSERT INTO subject VALUES ('s1','SQL joins','Learn SQL','BEGINNER',3,'Summary here','ACTIVE',1,1,NULL)")
        connection.execSQL("INSERT INTO chapter VALUES ('c1','s1',0,'Basics','Write joins',NULL,1,NULL)")
        connection.execSQL("INSERT INTO topic VALUES ('t1','c1',0,'Inner joins','[\"Write one\"]','[]',15,NULL,1,NULL)")
        connection.close()

        val db = Room.databaseBuilder<AppDatabase>(context, file.path).setDriver(AndroidSQLiteDriver()).build()
        val outline = RoomSubjectRepository(db, TestClock()).observeOutline("s1").first()!!
        assertThat(outline.subject.title).isEqualTo("SQL joins")
        assertThat(outline.subject.clarifications).isEmpty()
        assertThat(outline.chapters.single().topics.single().completedAt).isNull()

        // New v2 tables work on the migrated database.
        val learning = LearningRepository(db, TestClock())
        learning.applySession("t1", "Inner joins", listOf(true))
        assertThat(learning.observeProgress().first().topicsTotal).isEqualTo(1)
        db.close()
    }

    @Test fun chapterPassedBeforePracticalsStaysFinishedAfterMigrationToVersion3() = runBlocking {
        val (file, connection) = createAt(2, "migration-test-v3.db")
        connection.execSQL("INSERT INTO subject VALUES ('s1','SQL joins','Learn SQL','BEGINNER',3,'Summary','ACTIVE',NULL,1,1,NULL)")
        connection.execSQL(
            "INSERT INTO chapter VALUES ('c1','s1',0,'Basics','Write joins','{\"score\":0.9,\"passed\":true,\"completedAt\":1,\"variant\":1}',1,NULL)",
        )
        connection.execSQL("INSERT INTO topic VALUES ('t1','c1',0,'Inner joins','[\"Write one\"]','[]',15,NULL,5,1,NULL)")
        connection.close()

        val db = Room.databaseBuilder<AppDatabase>(context, file.path).setDriver(AndroidSQLiteDriver()).build()
        val chapter = RoomSubjectRepository(db, TestClock()).observeOutline("s1").first()!!.chapters.single()
        assertThat(chapter.practical).isNull()
        assertThat(chapter.practicalRequired).isFalse()
        assertThat(chapter.isFinished).isTrue()
        assertThat(LearningRepository(db, TestClock()).observeProgress().first().chaptersPassed).isEqualTo(1)
        db.close()
    }
}
