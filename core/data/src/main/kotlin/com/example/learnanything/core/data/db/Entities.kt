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
    /** JSON list of Clarification (question + learner's answer). */
    val clarificationsJson: String? = null,
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
    /** JSON of the best practical result (M2+). */
    val practicalResult: String? = null,
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
    val completedAt: Long? = null,
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

/**
 * A question. Owned by a topic (try-it and check questions) or a chapter (quiz).
 * [topicId] is the topic it tests, which is the owner for topic items.
 */
@Entity(tableName = "item", indices = [Index("ownerId"), Index("topicId")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val role: String,
    val topicId: String,
    val position: Int,
    val kind: String,
    /** QuestionDto JSON, exactly as validated. */
    val payloadJson: String,
    /** Quiz retake number; 0 for topic items. */
    val variant: Int = 0,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "attempt", indices = [Index("itemId")])
data class AttemptEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    /** Answer JSON. */
    val answerJson: String,
    val correct: Boolean,
    val score: Double,
    /** EXACT (checked by code) or AI_GRADED (M2+). */
    val trust: String,
    /** Test results or rubric feedback as JSON (practicals, M2+). */
    val feedbackJson: String? = null,
    val hintsUsed: Int,
    /** LESSON, QUIZ or REVIEW. */
    val context: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Learning state of one concept. M1 has one concept per topic. */
@Entity(tableName = "concept", indices = [Index("topicId", unique = true)])
data class ConceptEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val name: String,
    val mastery: Double,
    val step: Int,
    val nextReviewAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A video or simulation chosen for a topic. [sourceRef] is a YouTube video id or a PhET catalog key. */
@Entity(tableName = "resource", indices = [Index("topicId")])
data class ResourceEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    /** YOUTUBE, PHET or SEARCH (a query to show as a search chip). */
    val type: String,
    val sourceRef: String,
    val title: String,
    val startSeconds: Int? = null,
    val endSeconds: Int? = null,
    /** 1 (thumbs up) or -1 (thumbs down), null when not rated. */
    val rating: Int? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** One rung of the "I don't get it" ladder that the learner tried, and whether it helped. */
@Entity(tableName = "rescue_event", indices = [Index("topicId")])
data class RescueEventEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val rung: String,
    val helped: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** About the learner, used for analogies. One row with id "me". */
@Entity(tableName = "learner_profile")
data class LearnerProfileEntity(
    @PrimaryKey val id: String,
    val background: String,
    val knownSkills: String,
    /** JSON list of rung names, best first, learnt from rescue events. */
    val ladderOrderJson: String? = null,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** Projection used by progress screens. */
data class TopicProgressRow(
    val topicId: String,
    val subjectId: String,
    val completedAt: Long?,
)
