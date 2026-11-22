package com.example.learnanything.core.data.repository

import androidx.room3.withWriteTransaction
import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.db.ChapterEntity
import com.example.learnanything.core.data.db.SubjectEntity
import com.example.learnanything.core.data.db.TopicEntity
import com.example.learnanything.core.model.Chapter
import com.example.learnanything.core.model.ChapterGate
import com.example.learnanything.core.model.PracticalResult
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.ModelJson
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.SubjectOutline
import com.example.learnanything.core.model.SubjectStatus
import com.example.learnanything.core.model.Topic
import com.example.learnanything.core.model.clarify.Clarification
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

/** Courses: subjects, chapters and topics, including outline editing. */
interface SubjectRepository {
    fun observeSubjects(): Flow<List<Subject>>
    fun observeOutline(subjectId: String): Flow<SubjectOutline?>
    suspend fun outline(subjectId: String): SubjectOutline?

    /** Saves a validated outline as a new subject. Returns the new subject id. */
    suspend fun saveOutline(
        request: OutlineRequest,
        outline: OutlineResponse,
        clarifications: List<Clarification> = emptyList(),
        draft: Boolean = false,
    ): String

    suspend fun delete(subjectId: String)

    // Outline editing (draft courses; also allowed later).
    suspend fun activate(subjectId: String)
    suspend fun renameChapter(chapterId: String, title: String)
    suspend fun renameTopic(topicId: String, title: String)
    suspend fun moveChapter(chapterId: String, up: Boolean)
    suspend fun moveTopic(topicId: String, up: Boolean)
    suspend fun deleteChapter(chapterId: String)
    suspend fun deleteTopic(topicId: String)
    suspend fun addTopic(chapterId: String, title: String)

    /** Marks the subject as just used. */
    suspend fun touch(subjectId: String)
}

@Singleton
class RoomSubjectRepository @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeSource,
) : SubjectRepository {
    private val dao = db.subjectDao()
    private val stringList = ListSerializer(String.serializer())
    private val clarificationList = ListSerializer(Clarification.serializer())

    override fun observeSubjects(): Flow<List<Subject>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeOutline(subjectId: String): Flow<SubjectOutline?> = combine(
        dao.observe(subjectId),
        dao.observeChapters(subjectId),
        dao.observeTopics(subjectId),
    ) { subject, chapters, topics -> subject?.let { assemble(it, chapters, topics) } }

    override suspend fun outline(subjectId: String): SubjectOutline? {
        val subject = dao.subject(subjectId) ?: return null
        val chapters = dao.chapters(subjectId)
        return assemble(subject, chapters, chapters.flatMap { dao.topics(it.id) })
    }

    private fun assemble(subject: SubjectEntity, chapters: List<ChapterEntity>, topics: List<TopicEntity>): SubjectOutline {
        val byChapter = topics.groupBy { it.chapterId }
        return SubjectOutline(
            subject = subject.toDomain(),
            chapters = chapters.map { chapter ->
                Chapter(
                    id = chapter.id,
                    order = chapter.position,
                    title = chapter.title,
                    goal = chapter.goal,
                    topics = byChapter[chapter.id].orEmpty().sortedBy { it.position }.map { it.toDomain() },
                    gate = chapter.gateResult?.let { runCatching { ModelJson.decodeFromString(ChapterGate.serializer(), it) }.getOrNull() },
                    practical = chapter.practicalResult?.let { runCatching { ModelJson.decodeFromString(PracticalResult.serializer(), it) }.getOrNull() },
                )
            },
        )
    }

    override suspend fun saveOutline(
        request: OutlineRequest,
        outline: OutlineResponse,
        clarifications: List<Clarification>,
        draft: Boolean,
    ): String {
        val now = time.now()
        val subjectId = newId()
        val subject = SubjectEntity(
            id = subjectId,
            title = outline.title.trim(),
            goal = request.goal.trim(),
            level = request.level.name,
            weeklyHours = request.weeklyHours,
            summary = outline.summary.trim(),
            status = (if (draft) SubjectStatus.DRAFT else SubjectStatus.ACTIVE).name,
            clarificationsJson = clarifications.takeIf { it.isNotEmpty() }?.let { ModelJson.encodeToString(clarificationList, it) },
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
                    objectivesJson = ModelJson.encodeToString(stringList, topic.objectives),
                    prerequisitesJson = ModelJson.encodeToString(stringList, topic.prerequisites),
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

    override suspend fun activate(subjectId: String) = dao.setStatus(subjectId, SubjectStatus.ACTIVE.name, time.now())

    override suspend fun renameChapter(chapterId: String, title: String) {
        val chapter = dao.chapter(chapterId) ?: return
        val clean = title.trim().take(120).ifEmpty { return }
        dao.upsertChapter(chapter.copy(title = clean, updatedAt = time.now()))
    }

    override suspend fun renameTopic(topicId: String, title: String) {
        val topic = dao.topic(topicId) ?: return
        val clean = title.trim().take(120).ifEmpty { return }
        dao.upsertTopic(topic.copy(title = clean, updatedAt = time.now()))
    }

    override suspend fun moveChapter(chapterId: String, up: Boolean) {
        val chapter = dao.chapter(chapterId) ?: return
        val siblings = dao.chapters(chapter.subjectId)
        val i = siblings.indexOfFirst { it.id == chapterId }
        val j = if (up) i - 1 else i + 1
        if (j !in siblings.indices) return
        val now = time.now()
        db.withWriteTransaction {
            renumber(siblings.toMutableList().apply { add(j, removeAt(i)) }).forEach { (c, pos) ->
                if (c.position != pos) dao.upsertChapter(c.copy(position = pos, updatedAt = now))
            }
        }
    }

    override suspend fun moveTopic(topicId: String, up: Boolean) {
        val topic = dao.topic(topicId) ?: return
        val siblings = dao.topics(topic.chapterId)
        val i = siblings.indexOfFirst { it.id == topicId }
        val j = if (up) i - 1 else i + 1
        if (j !in siblings.indices) return
        val now = time.now()
        db.withWriteTransaction {
            renumber(siblings.toMutableList().apply { add(j, removeAt(i)) }).forEach { (t, pos) ->
                if (t.position != pos) dao.upsertTopic(t.copy(position = pos, updatedAt = now))
            }
        }
    }

    override suspend fun deleteChapter(chapterId: String) {
        val chapter = dao.chapter(chapterId) ?: return
        val now = time.now()
        db.withWriteTransaction {
            dao.tombstoneTopicsOf(chapterId, now)
            dao.tombstoneChapter(chapterId, now)
            renumber(dao.chapters(chapter.subjectId)).forEach { (c, pos) ->
                if (c.position != pos) dao.upsertChapter(c.copy(position = pos, updatedAt = now))
            }
        }
    }

    override suspend fun deleteTopic(topicId: String) {
        val topic = dao.topic(topicId) ?: return
        val now = time.now()
        db.withWriteTransaction {
            dao.tombstoneTopic(topicId, now)
            renumber(dao.topics(topic.chapterId)).forEach { (t, pos) ->
                if (t.position != pos) dao.upsertTopic(t.copy(position = pos, updatedAt = now))
            }
        }
    }

    override suspend fun addTopic(chapterId: String, title: String) {
        val clean = title.trim().take(120).ifEmpty { return }
        val position = dao.topics(chapterId).size
        dao.upsertTopic(
            TopicEntity(
                id = newId(),
                chapterId = chapterId,
                position = position,
                title = clean,
                objectivesJson = ModelJson.encodeToString(stringList, listOf("Understand $clean")),
                prerequisitesJson = "[]",
                estimatedMinutes = 15,
                updatedAt = time.now(),
            ),
        )
    }

    override suspend fun touch(subjectId: String) = dao.touch(subjectId, time.now())

    private fun <T> renumber(items: List<T>): List<Pair<T, Int>> = items.mapIndexed { i, t -> t to i }

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
        clarifications = clarificationsJson
            ?.let { runCatching { ModelJson.decodeFromString(clarificationList, it) }.getOrNull() }
            .orEmpty(),
    )

    private fun TopicEntity.toDomain() = Topic(
        id = id,
        order = position,
        title = title,
        objectives = ModelJson.decodeFromString(stringList, objectivesJson),
        prerequisites = ModelJson.decodeFromString(stringList, prerequisitesJson),
        estimatedMinutes = estimatedMinutes,
        completedAt = completedAt,
    )
}
