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
) {
    val topicsDone: Boolean get() = topics.isNotEmpty() && topics.all { it.isComplete }
    /** M1: a chapter is finished when every topic is done and the quiz is passed (practicals join in M2). */
    val isFinished: Boolean get() = topicsDone && gate?.passed == true
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
)

data class SubjectOutline(val subject: Subject, val chapters: List<Chapter>) {
    val topicCount: Int get() = chapters.sumOf { it.topics.size }
    val completedTopicCount: Int get() = chapters.sumOf { c -> c.topics.count { it.isComplete } }
}

/** What the learner should do next in a course. */
sealed interface NextStep {
    data class OpenTopic(val topicId: String, val title: String, val chapterTitle: String) : NextStep
    data class TakeQuiz(val chapterId: String, val chapterTitle: String) : NextStep
    data object CourseComplete : NextStep

    companion object {
        /** The first unfinished chapter decides: its first open topic, else its quiz. */
        fun of(outline: SubjectOutline): NextStep {
            val chapter = outline.chapters.firstOrNull { it.topics.isNotEmpty() && !it.isFinished }
                ?: return CourseComplete
            val topic = chapter.topics.firstOrNull { !it.isComplete }
            return if (topic != null) OpenTopic(topic.id, topic.title, chapter.title)
            else TakeQuiz(chapter.id, chapter.title)
        }
    }
}
