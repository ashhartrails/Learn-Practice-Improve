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
}

@Dao
interface AiCacheDao {
    @Query("SELECT response FROM ai_cache WHERE promptHash = :hash")
    suspend fun get(hash: String): String?

    @Upsert suspend fun put(entry: AiCacheEntity)
}
