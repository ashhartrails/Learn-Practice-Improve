package com.example.learnanything.core.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/*
 * Sync rules for every table (needed for the M4 Drive merge):
 * - `id` is a random UUID string, so rows from two phones never collide.
 * - `updatedAt` (epoch millis) changes on every write; newest wins on merge.
 * - Deletes set `deletedAt` (a tombstone) instead of removing the row.
 */

@Entity(tableName = "subject")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val goal: String,
    val level: String,
    val weeklyHours: Int,
    val summary: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "chapter",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("subjectId")],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val position: Int,
    val title: String,
    val goal: String,
    /** JSON of the chapter gate result once the quiz and practical are done (M1+). */
    val gateResult: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "topic",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("chapterId")],
)
data class TopicEntity(
    @PrimaryKey val id: String,
    val chapterId: String,
    val position: Int,
    val title: String,
    /** JSON array of strings. */
    val objectivesJson: String,
    /** JSON array of strings. */
    val prerequisitesJson: String,
    val estimatedMinutes: Int,
    /** Generated lazily when the topic is first opened (M1). */
    val lessonJson: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Validated AI responses keyed by prompt hash. Excluded from backups. */
@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey val promptHash: String,
    val callType: String,
    val response: String,
    val createdAt: Long,
    val updatedAt: Long,
)
