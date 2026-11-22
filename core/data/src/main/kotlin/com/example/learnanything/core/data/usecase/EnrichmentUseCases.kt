package com.example.learnanything.core.data.usecase

import com.example.learnanything.core.ai.CallProgress
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.StructuredResult
import com.example.learnanything.core.ai.youtube.VideoSearch
import com.example.learnanything.core.ai.youtube.VideoSearchException
import com.example.learnanything.core.ai.youtube.YouTubeSettings
import com.example.learnanything.core.data.repository.AttemptContext
import com.example.learnanything.core.data.repository.EnrichmentRepository
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.ResourceType
import com.example.learnanything.core.data.repository.StoredItem
import com.example.learnanything.core.data.repository.StoredResource
import com.example.learnanything.core.model.practical.GradePrompt
import com.example.learnanything.core.model.practical.GradeResponse
import com.example.learnanything.core.model.practical.GradeRules
import com.example.learnanything.core.model.practical.PracticalKind
import com.example.learnanything.core.model.practical.PracticalResponse
import com.example.learnanything.core.model.practical.RubricScore
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.GradeResult
import com.example.learnanything.core.model.rescue.RescueContext
import com.example.learnanything.core.model.rescue.RescuePrompt
import com.example.learnanything.core.model.rescue.RescueResponse
import com.example.learnanything.core.model.rescue.RescueRules
import com.example.learnanything.core.model.rescue.RescueRung
import com.example.learnanything.core.model.resources.PhetCatalog
import com.example.learnanything.core.model.resources.ResourcesContext
import com.example.learnanything.core.model.resources.ResourcesPrompt
import com.example.learnanything.core.model.resources.ResourcesResponse
import com.example.learnanything.core.model.resources.ResourcesRules
import javax.inject.Inject

sealed interface RescueContent {
    data class Written(val rung: RescueRung, val content: RescueResponse) : RescueContent
    data class Video(val resource: StoredResource) : RescueContent
    /** No YouTube key: show a chip that searches YouTube in the browser. */
    data class VideoSearchChip(val query: String, val note: String?) : RescueContent
    data class Simulation(val sim: PhetCatalog.Sim) : RescueContent
    /** No simulation fits: a description of a helpful diagram. */
    data class Diagram(val description: String) : RescueContent
    data class Failed(val message: UserMessage) : RescueContent
}

/** Topic resources (search queries, PhET key, diagram) from the AI, cached by prompt hash. */
class TopicResourcesUseCase @Inject constructor(
    private val findTopic: FindTopicUseCase,
    private val caller: StructuredCaller,
) {
    suspend operator fun invoke(topicId: String, onProgress: (CallProgress) -> Unit = {}): StructuredResult<ResourcesResponse> {
        val place = findTopic(topicId)
            ?: return StructuredResult.Failure(com.example.learnanything.core.ai.FailureKind.PROVIDER_ERROR, "This topic no longer exists.")
        val spec = ResourcesPrompt.build(
            ResourcesContext(place.subject.title, place.topic.title, place.topic.objectives, place.subject.level.label),
        )
        return caller.call(spec, ResourcesResponse.serializer(), ResourcesRules::check, onProgress)
    }
}

/** A short video for a topic: stored one, else search YouTube with the learner's key, else a search chip. */
class FindVideoUseCase @Inject constructor(
    private val resources: TopicResourcesUseCase,
    private val enrichment: EnrichmentRepository,
    private val search: VideoSearch,
    private val settings: YouTubeSettings,
) {
    suspend operator fun invoke(topicId: String, onProgress: (CallProgress) -> Unit = {}): RescueContent {
        enrichment.resource(topicId, ResourceType.YOUTUBE)?.let { return RescueContent.Video(it) }
        val r = when (val res = resources(topicId, onProgress)) {
            is StructuredResult.Success -> res.value
            is StructuredResult.Failure -> return RescueContent.Failed(FailureMessages.of(res.kind, res.message, res.retryAfterSeconds))
        }
        val query = r.searchQueries.first()
        val key = settings.youTubeKey()?.takeIf { it.isNotBlank() }
            ?: return RescueContent.VideoSearchChip(query, null)
        return try {
            var hit: com.example.learnanything.core.ai.youtube.VideoHit? = null
            for (q in r.searchQueries) {
                hit = search.search(q, key).firstOrNull()
                if (hit != null) break
            }
            if (hit == null) return RescueContent.VideoSearchChip(query, "No embeddable short video found.")
            RescueContent.Video(enrichment.saveResource(topicId, ResourceType.YOUTUBE, hit.videoId, hit.title))
        } catch (e: VideoSearchException) {
            RescueContent.VideoSearchChip(query, e.message)
        }
    }
}

/** A PhET simulation from the allow-listed catalog, or a diagram description when none fits. */
class FindSimulationUseCase @Inject constructor(
    private val resources: TopicResourcesUseCase,
    private val enrichment: EnrichmentRepository,
) {
    suspend operator fun invoke(topicId: String, onProgress: (CallProgress) -> Unit = {}): RescueContent {
        enrichment.resource(topicId, ResourceType.PHET)?.let { stored ->
            PhetCatalog.find(stored.sourceRef)?.let { return RescueContent.Simulation(it) }
        }
        val r = when (val res = resources(topicId, onProgress)) {
            is StructuredResult.Success -> res.value
            is StructuredResult.Failure -> return RescueContent.Failed(FailureMessages.of(res.kind, res.message, res.retryAfterSeconds))
        }
        val sim = PhetCatalog.find(r.phetKey)
        return if (sim != null) {
            enrichment.saveResource(topicId, ResourceType.PHET, sim.key, sim.title)
            RescueContent.Simulation(sim)
        } else {
            RescueContent.Diagram(r.visualDescription.ifBlank { "No simulation fits this topic. Try the next step." })
        }
    }
}

/** One rung of the rescue ladder. */
class GetRescueUseCase @Inject constructor(
    private val findTopic: FindTopicUseCase,
    private val caller: StructuredCaller,
    private val learning: LearningRepository,
    private val enrichment: EnrichmentRepository,
    private val findVideo: FindVideoUseCase,
    private val findSimulation: FindSimulationUseCase,
) {
    suspend operator fun invoke(topicId: String, rung: RescueRung, onProgress: (CallProgress) -> Unit = {}): RescueContent {
        if (rung == RescueRung.VIDEO) return findVideo(topicId, onProgress)
        if (rung == RescueRung.SIMULATION) return findSimulation(topicId, onProgress)
        val place = findTopic(topicId) ?: return RescueContent.Failed(UserMessage("This topic no longer exists."))
        val lesson = learning.storedLesson(topicId)?.lesson
        val profile = enrichment.profile()
        val spec = RescuePrompt.build(
            RescueContext(
                subjectTitle = place.subject.title,
                level = place.subject.level,
                topicTitle = place.topic.title,
                objectives = place.topic.objectives,
                prerequisites = place.topic.prerequisites,
                lessonSummary = lesson?.cards?.joinToString(" ") { "${it.heading}: ${it.body}" }.orEmpty(),
                background = profile.background,
                knownSkills = profile.knownSkills,
            ),
            rung,
        )
        val rules: (RescueResponse) -> List<String> = { RescueRules.check(it, rung) }
        return when (val r = caller.call(spec, RescueResponse.serializer(), rules, onProgress)) {
            is StructuredResult.Success -> RescueContent.Written(rung, r.value)
            is StructuredResult.Failure -> RescueContent.Failed(FailureMessages.of(r.kind, r.message, r.retryAfterSeconds))
        }
    }
}

/**
 * After a rung, the learner tries one of the lesson's check questions. A right answer means the
 * rung helped and the ladder stops; either way the result is recorded for next time.
 */
class RescueCheckUseCase @Inject constructor(
    private val learning: LearningRepository,
    private val enrichment: EnrichmentRepository,
) {
    /** The check question to try after [attempt] rungs (rotates through the lesson's questions). */
    suspend fun question(topicId: String, attempt: Int): StoredItem? {
        val lesson = learning.storedLesson(topicId) ?: return null
        val items = lesson.checks + lesson.tryIt
        return items[attempt % items.size]
    }

    suspend fun answer(topicId: String, rung: RescueRung, item: StoredItem, answer: Answer): GradeResult {
        val grade = com.example.learnanything.core.model.question.AnswerChecker.check(item.question, answer)
        learning.recordAttempt(item.id, answer, grade, AttemptContext.RESCUE)
        enrichment.recordRescue(topicId, rung, grade.correct)
        return grade
    }

    /** "Show me another way" without answering: the rung didn't help. */
    suspend fun skip(topicId: String, rung: RescueRung) = enrichment.recordRescue(topicId, rung, helped = false)
}

sealed interface TeachBackOutcome {
    data class Graded(val grade: GradeResponse, val score: RubricScore) : TeachBackOutcome
    data class Failed(val message: UserMessage) : TeachBackOutcome
}

/** Teach-back is open-ended, so a strong cloud model grades it against the rung's rubric (AI-graded). */
class GradeTeachBackUseCase @Inject constructor(private val caller: StructuredCaller) {
    suspend operator fun invoke(content: RescueResponse, answer: String, onProgress: (CallProgress) -> Unit = {}): TeachBackOutcome {
        if (answer.isBlank()) return TeachBackOutcome.Failed(UserMessage("Write your explanation first."))
        val task = PracticalResponse(PracticalKind.WRITTEN, content.title, content.body, rubric = content.rubric)
        val rules: (GradeResponse) -> List<String> = { GradeRules.check(it, content.rubric, answer) }
        return when (val r = caller.call(GradePrompt.build(task, answer), GradeResponse.serializer(), rules, onProgress)) {
            is StructuredResult.Success -> TeachBackOutcome.Graded(r.value, GradeRules.score(r.value, content.rubric))
            is StructuredResult.Failure -> TeachBackOutcome.Failed(FailureMessages.of(r.kind, r.message, r.retryAfterSeconds))
        }
    }
}

/** Saves the "About you" profile used for analogies. */
class SaveProfileUseCase @Inject constructor(private val enrichment: EnrichmentRepository) {
    suspend operator fun invoke(background: String, knownSkills: String) = enrichment.saveProfile(background, knownSkills)
}
