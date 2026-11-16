package com.example.learnanything.core.model

import com.example.learnanything.core.model.clarify.Clarification
import kotlinx.serialization.Serializable

/** Domain types used by screens. Room entities are mapped to these in core/data. */
data class Subject(
    val id: String,
    val title: String,
    val goal: String,
    val level: Level,
    val weeklyHours: Int,
    val summary: String,
    val status: SubjectStatus,
    val updatedAt: Long,
    val clarifications: List<Clarification> = emptyList(),
)

/** DRAFT while the learner edits the outline; ACTIVE once they start the course. */
enum class SubjectStatus { DRAFT, ACTIVE, ARCHIVED }

data class Chapter(
    val id: String,
    val order: Int,
    val title: String,
    val goal: String,
    val topics: List<Topic>,
    val gate: ChapterGate? = null,
    val practical: PracticalResult? = null,
) {
    val topicsDone: Boolean get() = topics.isNotEmpty() && topics.all { it.isComplete }
    val quizPassed: Boolean get() = gate?.passed == true
    /** Quizzes passed before M2 didn't need a practical, so those chapters stay finished. */
    val practicalRequired: Boolean get() = gate?.requiresPractical != false
    val practicalPassed: Boolean get() = practical?.passed == true
    /** Finished when every topic is done, the quiz is passed, and the practical is passed. */
    val isFinished: Boolean get() = topicsDone && quizPassed && (practicalPassed || !practicalRequired)
}

data class Topic(
    val id: String,
    val order: Int,
    val title: String,
    val objectives: List<String>,
    val prerequisites: List<String>,
    val estimatedMinutes: Int,
    val completedAt: Long? = null,
) {
    val isComplete: Boolean get() = completedAt != null
}

/** Best chapter quiz result. Stored as JSON in Chapter.gateResult. */
@Serializable
data class ChapterGate(
    /** 0.0 to 1.0. */
    val score: Double,
    val passed: Boolean,
    val completedAt: Long,
    val variant: Int,
    /** False only for quizzes passed before practicals existed (stored JSON without the field). */
    val requiresPractical: Boolean = false,
)

/** Best practical result. Stored as JSON in Chapter.practicalResult. */
@Serializable
data class PracticalResult(
    val score: Double,
    val passed: Boolean,
    /** EXACT (checked by code) or AI_GRADED. */
    val trust: String,
    val kind: String,
    val completedAt: Long,
)

data class SubjectOutline(val subject: Subject, val chapters: List<Chapter>) {
    val topicCount: Int get() = chapters.sumOf { it.topics.size }
    val completedTopicCount: Int get() = chapters.sumOf { c -> c.topics.count { it.isComplete } }
}

/** What the learner should do next in a course. */
sealed interface NextStep {
    data class OpenTopic(val topicId: String, val title: String, val chapterTitle: String) : NextStep
    data class TakeQuiz(val chapterId: String, val chapterTitle: String) : NextStep
    data class DoPractical(val chapterId: String, val chapterTitle: String) : NextStep
    data object CourseComplete : NextStep

    companion object {
        /** The first unfinished chapter decides: its first open topic, else its quiz, else its practical. */
        fun of(outline: SubjectOutline): NextStep {
            val chapter = outline.chapters.firstOrNull { it.topics.isNotEmpty() && !it.isFinished }
                ?: return CourseComplete
            val topic = chapter.topics.firstOrNull { !it.isComplete }
            return when {
                topic != null -> OpenTopic(topic.id, topic.title, chapter.title)
                !chapter.quizPassed -> TakeQuiz(chapter.id, chapter.title)
                else -> DoPractical(chapter.id, chapter.title)
            }
        }
    }
}
