package com.example.learnanything.core.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subject WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subject WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<SubjectEntity?>

    @Query("SELECT * FROM chapter WHERE subjectId = :subjectId AND deletedAt IS NULL ORDER BY position")
    fun observeChapters(subjectId: String): Flow<List<ChapterEntity>>

    @Query(
        """
        SELECT topic.* FROM topic
        JOIN chapter ON chapter.id = topic.chapterId
        WHERE chapter.subjectId = :subjectId AND topic.deletedAt IS NULL
        ORDER BY chapter.position, topic.position
        """,
    )
    fun observeTopics(subjectId: String): Flow<List<TopicEntity>>

    @Upsert suspend fun upsertSubject(subject: SubjectEntity)
    @Upsert suspend fun upsertChapters(chapters: List<ChapterEntity>)
    @Upsert suspend fun upsertTopics(topics: List<TopicEntity>)

    @Query("UPDATE subject SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun tombstoneSubject(id: String, now: Long)

    @Query("SELECT * FROM subject WHERE id = :id AND deletedAt IS NULL")
    suspend fun subject(id: String): SubjectEntity?

    @Query("SELECT * FROM chapter WHERE id = :id AND deletedAt IS NULL")
    suspend fun chapter(id: String): ChapterEntity?

    @Query("SELECT * FROM topic WHERE id = :id AND deletedAt IS NULL")
    suspend fun topic(id: String): TopicEntity?

    @Query("SELECT * FROM chapter WHERE subjectId = :subjectId AND deletedAt IS NULL ORDER BY position")
    suspend fun chapters(subjectId: String): List<ChapterEntity>

    @Query("SELECT * FROM topic WHERE chapterId = :chapterId AND deletedAt IS NULL ORDER BY position")
    suspend fun topics(chapterId: String): List<TopicEntity>

    @Upsert suspend fun upsertChapter(chapter: ChapterEntity)
    @Upsert suspend fun upsertTopic(topic: TopicEntity)

    @Query("UPDATE subject SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: Long)

    /** Marks the subject as recently used; Home's Continue card follows the latest one. */
    @Query("UPDATE subject SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("UPDATE chapter SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun tombstoneChapter(id: String, now: Long)

    @Query("UPDATE topic SET deletedAt = :now, updatedAt = :now WHERE chapterId = :chapterId AND deletedAt IS NULL")
    suspend fun tombstoneTopicsOf(chapterId: String, now: Long)

    @Query("UPDATE topic SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun tombstoneTopic(id: String, now: Long)

    @Query("UPDATE topic SET completedAt = :now, updatedAt = :now WHERE id = :id AND completedAt IS NULL")
    suspend fun completeTopic(id: String, now: Long)

    @Query("UPDATE topic SET lessonJson = :json, updatedAt = :now WHERE id = :id")
    suspend fun setLesson(id: String, json: String, now: Long)

    @Query("UPDATE chapter SET gateResult = :json, updatedAt = :now WHERE id = :id")
    suspend fun setGate(id: String, json: String, now: Long)

    @Query("UPDATE chapter SET practicalResult = :json, updatedAt = :now WHERE id = :id")
    suspend fun setPracticalResult(id: String, json: String, now: Long)

    @Query(
        """
        SELECT topic.id AS topicId, chapter.subjectId AS subjectId, topic.completedAt AS completedAt
        FROM topic JOIN chapter ON chapter.id = topic.chapterId
        JOIN subject ON subject.id = chapter.subjectId
        WHERE topic.deletedAt IS NULL AND chapter.deletedAt IS NULL AND subject.deletedAt IS NULL
        """,
    )
    fun observeTopicProgress(): Flow<List<TopicProgressRow>>

    @Query("SELECT * FROM chapter WHERE deletedAt IS NULL")
    fun observeAllChapters(): Flow<List<ChapterEntity>>
}

@Dao
interface LearningDao {
    @Query("SELECT * FROM item WHERE ownerId = :ownerId AND role = :role AND deletedAt IS NULL ORDER BY variant DESC, position")
    suspend fun items(ownerId: String, role: String): List<ItemEntity>

    @Query("SELECT * FROM item WHERE topicId = :topicId AND role = :role AND deletedAt IS NULL ORDER BY position")
    suspend fun itemsForTopic(topicId: String, role: String): List<ItemEntity>

    @Query("SELECT * FROM item WHERE id = :id")
    suspend fun item(id: String): ItemEntity?

    @Upsert suspend fun upsertItems(items: List<ItemEntity>)

    @Query("UPDATE item SET deletedAt = :now, updatedAt = :now WHERE ownerId = :ownerId AND role = :role AND deletedAt IS NULL")
    suspend fun tombstoneItems(ownerId: String, role: String, now: Long)

    @Upsert suspend fun upsertAttempt(attempt: AttemptEntity)

    @Query("SELECT * FROM attempt WHERE itemId IN (:itemIds) AND deletedAt IS NULL ORDER BY createdAt")
    suspend fun attemptsFor(itemIds: List<String>): List<AttemptEntity>

    @Query("SELECT createdAt FROM attempt WHERE deletedAt IS NULL")
    fun observeAttemptTimes(): Flow<List<Long>>

    @Query("SELECT * FROM concept WHERE topicId = :topicId AND deletedAt IS NULL")
    suspend fun concept(topicId: String): ConceptEntity?

    @Upsert suspend fun upsertConcept(concept: ConceptEntity)

    @Query("SELECT * FROM concept WHERE deletedAt IS NULL")
    fun observeConcepts(): Flow<List<ConceptEntity>>
}

@Dao
interface AiCacheDao {
    @Query("SELECT response FROM ai_cache WHERE promptHash = :hash")
    suspend fun get(hash: String): String?

    @Upsert suspend fun put(entry: AiCacheEntity)
}

@Dao
interface EnrichmentDao {
    @Query("SELECT * FROM resource WHERE topicId = :topicId AND type = :type AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun resource(topicId: String, type: String): ResourceEntity?

    @Upsert suspend fun upsertResource(resource: ResourceEntity)

    @Query("UPDATE resource SET deletedAt = :now, updatedAt = :now WHERE topicId = :topicId AND type = :type AND deletedAt IS NULL")
    suspend fun tombstoneResources(topicId: String, type: String, now: Long)

    @Upsert suspend fun upsertRescueEvent(event: RescueEventEntity)

    @Query("SELECT rung, COUNT(*) AS helpedCount FROM rescue_event WHERE helped = 1 AND deletedAt IS NULL GROUP BY rung")
    suspend fun helpedCounts(): List<RungCount>

    @Query("SELECT * FROM learner_profile WHERE id = :id AND deletedAt IS NULL")
    suspend fun profile(id: String): LearnerProfileEntity?

    @Query("SELECT * FROM learner_profile WHERE id = :id AND deletedAt IS NULL")
    fun observeProfile(id: String): Flow<LearnerProfileEntity?>

    @Upsert suspend fun upsertProfile(profile: LearnerProfileEntity)
}

data class RungCount(val rung: String, val helpedCount: Int)
