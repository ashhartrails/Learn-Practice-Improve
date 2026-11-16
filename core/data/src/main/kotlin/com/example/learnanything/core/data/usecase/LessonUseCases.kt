package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.CallProgress
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.StructuredResult
import com.example.learnanything.core.data.repository.AttemptContext
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.StoredItem
import com.example.learnanything.core.data.repository.StoredLesson
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.model.Chapter
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.Topic
import com.example.learnanything.core.model.lesson.LessonContext
import com.example.learnanything.core.model.lesson.LessonPrompt
import com.example.learnanything.core.model.lesson.LessonResponse
import com.example.learnanything.core.model.lesson.LessonRules
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.AnswerChecker
import com.example.learnanything.core.model.question.GradeResult
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class TopicPlace(val subject: Subject, val chapter: Chapter, val topic: Topic, val earlierTopics: List<Topic>)

sealed interface LessonResult {
    data class Ready(val place: TopicPlace, val lesson: StoredLesson) : LessonResult
    data class Failed(val message: UserMessage) : LessonResult
}

/** Finds a topic with its subject and chapter. */
class FindTopicUseCase @Inject constructor(private val subjects: SubjectRepository) {
    suspend operator fun invoke(topicId: String): TopicPlace? {
        // Topic ids are unique; scan the learner's courses (a handful) to find it.
        for (subject in subjectsList()) {
            val outline = subjects.outline(subject.id) ?: continue
            val all = outline.chapters.flatMap { c -> c.topics.map { c to it } }
            val index = all.indexOfFirst { it.second.id == topicId }
            if (index >= 0) {
                val (chapter, topic) = all[index]
                return TopicPlace(outline.subject, chapter, topic, all.take(index).map { it.second })
            }
        }
        return null
    }

    private suspend fun subjectsList() = subjects.observeSubjects().first()
}

/** Lazy lessons: generated the first time a topic is opened, then read from Room. */
class OpenLessonUseCase @Inject constructor(
    private val findTopic: FindTopicUseCase,
    private val caller: StructuredCaller,
    private val learning: LearningRepository,
    private val subjects: SubjectRepository,
) {
    suspend operator fun invoke(topicId: String, onProgress: (CallProgress) -> Unit = {}): LessonResult {
        val place = findTopic(topicId) ?: return LessonResult.Failed(UserMessage("This topic no longer exists."))
        subjects.touch(place.subject.id)
        learning.storedLesson(topicId)?.let { return LessonResult.Ready(place, it) }

        val spec = LessonPrompt.build(
            LessonContext(
                subjectTitle = place.subject.title,
                subjectGoal = place.subject.goal,
                level = place.subject.level,
                clarifications = place.subject.clarifications,
                chapterTitle = place.chapter.title,
                chapterGoal = place.chapter.goal,
                topicTitle = place.topic.title,
                objectives = place.topic.objectives,
                prerequisites = place.topic.prerequisites,
                earlierTopics = place.earlierTopics.map { it.title },
            ),
        )
        return when (val r = caller.call(spec, LessonResponse.serializer(), LessonRules::check, onProgress)) {
            is StructuredResult.Success -> LessonResult.Ready(place, learning.saveLesson(topicId, r.value))
            is StructuredResult.Failure -> LessonResult.Failed(FailureMessages.of(r.kind, r.message, r.retryAfterSeconds))
        }
    }
}

/** Grades one answer by code and records the attempt. */
class GradeAnswerUseCase @Inject constructor(private val learning: LearningRepository) {
    suspend operator fun invoke(item: StoredItem, answer: Answer, context: AttemptContext): GradeResult {
        val grade = AnswerChecker.check(item.question, answer)
        learning.recordAttempt(item.id, answer, grade, context)
        return grade
    }
}

/** Marks a topic done and feeds its check-question results into mastery. */
class FinishTopicUseCase @Inject constructor(private val learning: LearningRepository) {
    suspend operator fun invoke(topic: Topic, checkResults: List<Boolean>) {
        learning.applySession(topic.id, topic.title, checkResults)
        learning.completeTopic(topic.id)
    }
}

/** A review answer: grade, record, and move that topic's concept on the review ladder. */
class ReviewAnswerUseCase @Inject constructor(
    private val grade: GradeAnswerUseCase,
    private val learning: LearningRepository,
) {
    suspend operator fun invoke(item: StoredItem, topicTitle: String, answer: Answer): GradeResult {
        val result = grade(item, answer, AttemptContext.REVIEW)
        learning.applySession(item.topicId, topicTitle, listOf(result.correct))
        return result
    }
}
