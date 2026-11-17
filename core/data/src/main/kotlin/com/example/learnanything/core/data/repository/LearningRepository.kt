package com.example.learnanything.core.data.repository

import androidx.room3.withWriteTransaction
import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.db.AttemptEntity
import com.example.learnanything.core.data.db.ChapterEntity
import com.example.learnanything.core.data.db.ConceptEntity
import com.example.learnanything.core.data.db.ItemEntity
import com.example.learnanything.core.model.ChapterGate
import com.example.learnanything.core.model.PracticalResult
import com.example.learnanything.core.model.practical.PracticalResponse
import com.example.learnanything.core.model.ModelJson
import com.example.learnanything.core.model.learning.ConceptState
import com.example.learnanything.core.model.learning.MasteryScheduler
import com.example.learnanything.core.model.lesson.LessonResponse
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.GradeResult
import com.example.learnanything.core.model.question.Question
import com.example.learnanything.core.model.question.QuestionDto
import com.example.learnanything.core.model.question.toQuestion
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ItemRole { TRY_IT, CHECK, QUIZ, PRACTICAL }
enum class AttemptContext { LESSON, QUIZ, REVIEW, PRACTICAL, RESCUE }

/** A stored, validated question ready to show. */
data class StoredItem(val id: String, val role: ItemRole, val topicId: String, val question: Question)

data class StoredLesson(val lesson: LessonResponse, val tryIt: StoredItem, val checks: List<StoredItem>)

data class StoredPractical(val itemId: String, val variant: Int, val practical: PracticalResponse)

data class ReviewItem(val topicId: String, val topicTitle: String, val item: StoredItem)

data class ProgressSummary(
    val topicsCompleted: Int,
    val topicsTotal: Int,
    val chaptersPassed: Int,
    val chaptersTotal: Int,
    val averageMastery: Double?,
    val reviewsDue: Int,
    val streakDays: Int,
    /** Per subject: completed and total topics. */
    val perSubject: Map<String, Pair<Int, Int>>,
)

/** Lessons, questions, attempts, mastery and progress. */
@Singleton
class LearningRepository @Inject constructor(
    private val db: AppDatabase,
    private val time: TimeSource,
) {
    private val dao = db.learningDao()
    private val courseDao = db.subjectDao()

    suspend fun storedLesson(topicId: String): StoredLesson? {
        val topic = courseDao.topic(topicId) ?: return null
        val json = topic.lessonJson ?: return null
        val lesson = runCatching { ModelJson.decodeFromString(LessonResponse.serializer(), json) }.getOrNull() ?: return null
        val tryIt = dao.items(topicId, ItemRole.TRY_IT.name).firstOrNull()?.toStored() ?: return null
        val checks = dao.items(topicId, ItemRole.CHECK.name).map { it.toStored() }
        if (checks.isEmpty()) return null
        return StoredLesson(lesson, tryIt, checks)
    }

    /** Saves a validated lesson and its questions. [lessonJson] is the exact validated JSON. */
    suspend fun saveLesson(topicId: String, lesson: LessonResponse): StoredLesson {
        val now = time.now()
        val tryIt = item(topicId, ItemRole.TRY_IT, 0, lesson.tryIt, "TOPIC", topicId, 0, now)
        val checks = lesson.checks.mapIndexed { i, q -> item(topicId, ItemRole.CHECK, i, q, "TOPIC", topicId, 0, now) }
        db.withWriteTransaction {
            dao.tombstoneItems(topicId, ItemRole.TRY_IT.name, now)
            dao.tombstoneItems(topicId, ItemRole.CHECK.name, now)
            dao.upsertItems(listOf(tryIt) + checks)
            courseDao.setLesson(topicId, ModelJson.encodeToString(LessonResponse.serializer(), lesson), now)
        }
        return StoredLesson(lesson, tryIt.toStored(), checks.map { it.toStored() })
    }

    /** The newest quiz variant for a chapter, or empty if none yet. */
    suspend fun quizItems(chapterId: String): List<StoredItem> {
        val all = dao.items(chapterId, ItemRole.QUIZ.name)
        val newest = all.maxOfOrNull { it.variant } ?: return emptyList()
        return all.filter { it.variant == newest }.sortedBy { it.position }.map { it.toStored() }
    }

    suspend fun quizVariant(chapterId: String): Int =
        dao.items(chapterId, ItemRole.QUIZ.name).maxOfOrNull { it.variant } ?: 0

    suspend fun saveQuiz(chapterId: String, questions: List<Pair<String, QuestionDto>>, variant: Int): List<StoredItem> {
        val now = time.now()
        val items = questions.mapIndexed { i, (topicId, q) -> item(topicId, ItemRole.QUIZ, i, q, "CHAPTER", chapterId, variant, now) }
        db.withWriteTransaction {
            dao.tombstoneItems(chapterId, ItemRole.QUIZ.name, now)
            dao.upsertItems(items)
        }
        return items.map { it.toStored() }
    }

    suspend fun recordAttempt(itemId: String, answer: Answer, grade: GradeResult, context: AttemptContext, hintsUsed: Int = 0) {
        val now = time.now()
        dao.upsertAttempt(
            AttemptEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                answerJson = ModelJson.encodeToString(Answer.serializer(), answer),
                correct = grade.correct,
                score = grade.score,
                trust = "EXACT",
                hintsUsed = hintsUsed,
                context = context.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Applies one study session's results to a topic's concept, creating it if needed. */
    suspend fun applySession(topicId: String, topicTitle: String, results: List<Boolean>) {
        val now = time.now()
        val existing = dao.concept(topicId)
        val state = existing?.let { ConceptState(it.mastery, it.step, it.nextReviewAt) } ?: MasteryScheduler.initial(now)
        val next = MasteryScheduler.updateSession(state, results, now)
        dao.upsertConcept(
            ConceptEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                topicId = topicId,
                name = topicTitle,
                mastery = next.mastery,
                step = next.step,
                nextReviewAt = next.nextReviewAt,
                updatedAt = now,
            ),
        )
    }

    suspend fun completeTopic(topicId: String) = courseDao.completeTopic(topicId, time.now())

    /** Keeps the best result: a pass is never replaced by a later fail. */
    suspend fun recordGate(chapterId: String, gate: ChapterGate) {
        val chapter = courseDao.chapter(chapterId) ?: return
        val previous = chapter.gateResult?.let { runCatching { ModelJson.decodeFromString(ChapterGate.serializer(), it) }.getOrNull() }
        val best = if (previous != null && previous.score > gate.score) previous else gate
        // A chapter passed before practicals existed stays finished without one.
        val legacyPass = previous != null && previous.passed && !previous.requiresPractical
        val stored = if (legacyPass) best.copy(requiresPractical = false) else best
        courseDao.setGate(chapterId, ModelJson.encodeToString(ChapterGate.serializer(), stored), time.now())
    }

    /** The chapter's current practical, if one was generated. */
    suspend fun practical(chapterId: String): StoredPractical? {
        val item = dao.items(chapterId, ItemRole.PRACTICAL.name).maxByOrNull { it.variant } ?: return null
        val practical = runCatching { ModelJson.decodeFromString(PracticalResponse.serializer(), item.payloadJson) }.getOrNull()
            ?: return null
        return StoredPractical(item.id, item.variant, practical)
    }

    suspend fun practicalVariant(chapterId: String): Int =
        dao.items(chapterId, ItemRole.PRACTICAL.name).maxOfOrNull { it.variant } ?: 0

    /** Saves a validated (and, for code, verified) practical, replacing the previous one. */
    suspend fun savePractical(chapterId: String, topicId: String, practical: PracticalResponse, variant: Int): StoredPractical {
        val now = time.now()
        val entity = ItemEntity(
            id = UUID.randomUUID().toString(),
            ownerType = "CHAPTER",
            ownerId = chapterId,
            role = ItemRole.PRACTICAL.name,
            topicId = topicId,
            position = 0,
            kind = practical.kind.wire,
            payloadJson = ModelJson.encodeToString(PracticalResponse.serializer(), practical),
            variant = variant,
            updatedAt = now,
        )
        db.withWriteTransaction {
            dao.tombstoneItems(chapterId, ItemRole.PRACTICAL.name, now)
            dao.upsertItems(listOf(entity))
        }
        return StoredPractical(entity.id, variant, practical)
    }

    /** The learner's last submitted answer for a practical, so the workspace can restore it. */
    suspend fun lastPracticalAnswer(itemId: String): String? =
        dao.attemptsFor(listOf(itemId)).lastOrNull()?.answerJson
            ?.let { runCatching { (ModelJson.decodeFromString(Answer.serializer(), it) as? Answer.Text)?.text }.getOrNull() }

    suspend fun recordPracticalAttempt(itemId: String, answer: String, score: Double, passed: Boolean, trust: String, feedbackJson: String) {
        val now = time.now()
        dao.upsertAttempt(
            AttemptEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                answerJson = ModelJson.encodeToString(Answer.serializer(), Answer.Text(answer)),
                correct = passed,
                score = score,
                trust = trust,
                feedbackJson = feedbackJson,
                hintsUsed = 0,
                context = AttemptContext.PRACTICAL.name,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Keeps the best result: a pass is never replaced by a later fail. */
    suspend fun recordPracticalResult(chapterId: String, result: PracticalResult) {
        val chapter = courseDao.chapter(chapterId) ?: return
        val previous = chapter.practicalResult
            ?.let { runCatching { ModelJson.decodeFromString(PracticalResult.serializer(), it) }.getOrNull() }
        val keepPrevious = previous != null && (previous.passed && !result.passed || previous.passed == result.passed && previous.score > result.score)
        val best = if (keepPrevious) previous!! else result
        courseDao.setPracticalResult(chapterId, ModelJson.encodeToString(PracticalResult.serializer(), best), time.now())
    }

    /** One check question per topic whose review is due, for topics that still exist. */
    suspend fun dueReviews(now: Long = time.now(), limit: Int = 10): List<ReviewItem> {
        val result = mutableListOf<ReviewItem>()
        for (concept in dueConcepts(now)) {
            if (result.size >= limit) break
            val topic = courseDao.topic(concept.topicId) ?: continue
            val items = dao.itemsForTopic(concept.topicId, ItemRole.CHECK.name)
            if (items.isEmpty()) continue
            // Rotate: the question asked least recently (or never) comes first.
            val attempts = dao.attemptsFor(items.map { it.id }).groupBy { it.itemId }
            val pick = items.minBy { attempts[it.id]?.maxOf { a -> a.createdAt } ?: Long.MIN_VALUE }
            result += ReviewItem(concept.topicId, topic.title, pick.toStored())
        }
        return result
    }

    /** Due concepts whose topic belongs to a live course. Lists are small in a personal app. */
    private suspend fun dueConcepts(now: Long): List<ConceptEntity> {
        val liveTopics = courseDao.observeTopicProgress().first().map { it.topicId }.toSet()
        return dao.observeConcepts().first()
            .filter { it.nextReviewAt <= now && it.topicId in liveTopics }
            .sortedBy { it.nextReviewAt }
    }

    fun observeProgress(): Flow<ProgressSummary> = combine(
        courseDao.observeTopicProgress(),
        courseDao.observeAllChapters(),
        dao.observeConcepts(),
        dao.observeAttemptTimes(),
    ) { topics, chapters, concepts, attemptTimes ->
        val now = time.now()
        val liveTopicIds = topics.map { it.topicId }.toSet()
        val liveConcepts = concepts.filter { it.topicId in liveTopicIds }
        val liveSubjects = topics.map { it.subjectId }.toSet()
        val liveChapters = chapters.filter { it.subjectId in liveSubjects }
        ProgressSummary(
            topicsCompleted = topics.count { it.completedAt != null },
            topicsTotal = topics.size,
            chaptersPassed = liveChapters.count { c -> c.gateFinished() },
            chaptersTotal = liveChapters.size,
            averageMastery = liveConcepts.takeIf { it.isNotEmpty() }?.map { it.mastery }?.average(),
            reviewsDue = liveConcepts.count { it.nextReviewAt <= now },
            streakDays = Streak.days(attemptTimes, now),
            perSubject = topics.groupBy { it.subjectId }.mapValues { (_, rows) -> rows.count { it.completedAt != null } to rows.size },
        )
    }

    fun observeReviewsDueCount(): Flow<Int> = observeProgress().map { it.reviewsDue }

    private fun item(
        topicId: String, role: ItemRole, position: Int, q: QuestionDto,
        ownerType: String, ownerId: String, variant: Int, now: Long,
    ) = ItemEntity(
        id = UUID.randomUUID().toString(),
        ownerType = ownerType,
        ownerId = ownerId,
        role = role.name,
        topicId = topicId,
        position = position,
        kind = q.kind,
        payloadJson = ModelJson.encodeToString(QuestionDto.serializer(), q),
        variant = variant,
        updatedAt = now,
    )

    /** Quiz passed, and the practical passed unless the chapter was finished before practicals existed. */
    private fun ChapterEntity.gateFinished(): Boolean {
        val gate = gateResult?.let { runCatching { ModelJson.decodeFromString(ChapterGate.serializer(), it) }.getOrNull() } ?: return false
        if (!gate.passed) return false
        if (!gate.requiresPractical) return true
        return practicalResult?.let { runCatching { ModelJson.decodeFromString(PracticalResult.serializer(), it).passed }.getOrNull() } == true
    }

    private fun ItemEntity.toStored() = StoredItem(
        id = id,
        role = ItemRole.valueOf(role),
        topicId = topicId,
        question = ModelJson.decodeFromString(QuestionDto.serializer(), payloadJson).toQuestion(),
    )
}

/** Consecutive days (in the phone's time zone) with at least one answer, ending today or yesterday. */
object Streak {
    fun days(times: List<Long>, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int {
        val days = times.map { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.toSet()
        var day = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (day !in days) day = day.minusDays(1)
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
