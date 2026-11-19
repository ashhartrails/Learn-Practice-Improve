package com.example.learnanything.core.data

import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.data.usecase.StartCourseUseCase
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.SubjectStatus
import com.example.learnanything.core.model.clarify.Clarification
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CourseEditingTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: RoomSubjectRepository
    private lateinit var subjectId: String

    private fun t(title: String) = OutlineTopic(title, listOf("Do $title"), emptyList(), 10)

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        repo = RoomSubjectRepository(db, TestClock())
        subjectId = repo.saveOutline(
            OutlineRequest("SQL", Level.BEGINNER, 3),
            OutlineResponse("SQL joins", "Learn joins.", listOf(
                OutlineChapter("One", "Goal one", listOf(t("A"), t("B"), t("C"))),
                OutlineChapter("Two", "Goal two", listOf(t("D"))),
            )),
            clarifications = listOf(Clarification("Which database?", "PostgreSQL")),
            draft = true,
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun titles() = repo.outline(subjectId)!!.chapters.map { c -> c.title to c.topics.map { it.title } }

    @Test fun draftKeepsClarificationsAndActivates() = runTest {
        val outline = repo.outline(subjectId)!!
        assertThat(outline.subject.status).isEqualTo(SubjectStatus.DRAFT)
        assertThat(outline.subject.clarifications.single().answer).isEqualTo("PostgreSQL")
        assertThat(StartCourseUseCase(repo)(subjectId)).isTrue()
        assertThat(repo.outline(subjectId)!!.subject.status).isEqualTo(SubjectStatus.ACTIVE)
    }

    @Test fun renameMoveDeleteAndAdd() = runTest {
        val one = repo.outline(subjectId)!!.chapters[0]
        repo.renameTopic(one.topics[0].id, "  Alpha ")
        repo.moveTopic(one.topics[2].id, up = true)
        assertThat(titles()[0].second).containsExactly("Alpha", "C", "B").inOrder()

        repo.deleteTopic(one.topics[0].id)
        repo.addTopic(one.id, "New topic")
        val chapter = repo.outline(subjectId)!!.chapters[0]
        assertThat(chapter.topics.map { it.title }).containsExactly("C", "B", "New topic").inOrder()
        assertThat(chapter.topics.map { it.order }).containsExactly(0, 1, 2).inOrder()

        repo.moveChapter(repo.outline(subjectId)!!.chapters[1].id, up = true)
        repo.renameChapter(repo.outline(subjectId)!!.chapters[0].id, "Second, now first")
        assertThat(titles().map { it.first }).containsExactly("Second, now first", "One").inOrder()
    }

    @Test fun movingPastTheEdgeDoesNothing() = runTest {
        val first = repo.outline(subjectId)!!.chapters[0]
        repo.moveChapter(first.id, up = true)
        repo.moveTopic(first.topics[0].id, up = true)
        assertThat(titles()[0]).isEqualTo("One" to listOf("A", "B", "C"))
    }

    @Test fun blankRenameIsIgnored() = runTest {
        val chapter = repo.outline(subjectId)!!.chapters[0]
        repo.renameChapter(chapter.id, "   ")
        assertThat(titles()[0].first).isEqualTo("One")
    }

    @Test fun deletingAChapterRemovesItsTopicsAndEmptyCourseCannotStart() = runTest {
        repo.outline(subjectId)!!.chapters.forEach { repo.deleteChapter(it.id) }
        assertThat(repo.outline(subjectId)!!.chapters).isEmpty()
        assertThat(StartCourseUseCase(repo)(subjectId)).isFalse()
    }
}
