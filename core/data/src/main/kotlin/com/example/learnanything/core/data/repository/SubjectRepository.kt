package com.example.learnanything.core.data.repository

import androidx.room3.withWriteTransaction
import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.db.ChapterEntity
import com.example.learnanything.core.data.db.SubjectEntity
import com.example.learnanything.core.data.db.TopicEntity
import com.example.learnanything.core.model.Chapter
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.SubjectOutline
import com.example.learnanything.core.model.SubjectStatus
import com.example.learnanything.core.model.Topic
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface SubjectRepository {
    fun observeSubjects(): Flow<List<Subject>>
    fun observeOutline(subjectId: String): Flow<SubjectOutline?>
    /** Saves a validated outline as a new subject. Returns the new subject id. */
    suspend fun saveOutline(request: OutlineRequest, outline: OutlineResponse): String
    suspend fun delete(subjectId: String)
}

@Singleton
class RoomSubjectRepository @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeSource,
) : SubjectRepository {
    private val dao = db.subjectDao()
    private val stringList = ListSerializer(String.serializer())

    override fun observeSubjects(): Flow<List<Subject>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeOutline(subjectId: String): Flow<SubjectOutline?> = combine(
        dao.observe(subjectId),
        dao.observeChapters(subjectId),
        dao.observeTopics(subjectId),
    ) { subject, chapters, topics ->
        subject ?: return@combine null
        val byChapter = topics.groupBy { it.chapterId }
        SubjectOutline(
            subject = subject.toDomain(),
            chapters = chapters.map { chapter ->
                Chapter(
                    id = chapter.id,
                    order = chapter.position,
                    title = chapter.title,
                    goal = chapter.goal,
                    topics = byChapter[chapter.id].orEmpty().map { it.toDomain() },
                )
            },
        )
    }

    override suspend fun saveOutline(request: OutlineRequest, outline: OutlineResponse): String {
        val now = time.now()
        val subjectId = newId()
        val subject = SubjectEntity(
            id = subjectId,
            title = outline.title.trim(),
            goal = request.goal.trim(),
            level = request.level.name,
            weeklyHours = request.weeklyHours,
            summary = outline.summary.trim(),
            status = SubjectStatus.ACTIVE.name,
            createdAt = now,
            updatedAt = now,
        )
        val chapters = mutableListOf<ChapterEntity>()
        val topics = mutableListOf<TopicEntity>()
        outline.chapters.forEachIndexed { ci, chapter ->
            val chapterId = newId()
            chapters += ChapterEntity(chapterId, subjectId, ci, chapter.title.trim(), chapter.goal.trim(), updatedAt = now)
            chapter.topics.forEachIndexed { ti, topic ->
                topics += TopicEntity(
                    id = newId(),
                    chapterId = chapterId,
                    position = ti,
                    title = topic.title.trim(),
                    objectivesJson = Json.encodeToString(stringList, topic.objectives),
                    prerequisitesJson = Json.encodeToString(stringList, topic.prerequisites),
                    estimatedMinutes = topic.estimatedMinutes,
                    updatedAt = now,
                )
            }
        }
        db.withWriteTransaction {
            dao.upsertSubject(subject)
            dao.upsertChapters(chapters)
            dao.upsertTopics(topics)
        }
        return subjectId
    }

    override suspend fun delete(subjectId: String) = dao.tombstoneSubject(subjectId, time.now())

    private fun newId() = UUID.randomUUID().toString()

    private fun SubjectEntity.toDomain() = Subject(
        id = id,
        title = title,
        goal = goal,
        level = runCatching { Level.valueOf(level) }.getOrDefault(Level.BEGINNER),
        weeklyHours = weeklyHours,
        summary = summary,
        status = runCatching { SubjectStatus.valueOf(status) }.getOrDefault(SubjectStatus.ACTIVE),
        updatedAt = updatedAt,
    )

    private fun TopicEntity.toDomain() = Topic(
        id = id,
        order = position,
        title = title,
        objectives = Json.decodeFromString(stringList, objectivesJson),
        prerequisites = Json.decodeFromString(stringList, prerequisitesJson),
        estimatedMinutes = estimatedMinutes,
    )
}
