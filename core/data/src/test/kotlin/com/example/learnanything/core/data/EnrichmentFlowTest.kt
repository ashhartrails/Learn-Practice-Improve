package com.example.learnanything.core.data

import com.example.learnanything.core.ai.ProviderRouter
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.validation.JsonSchemaValidator
import com.example.learnanything.core.ai.youtube.VideoHit
import com.example.learnanything.core.ai.youtube.VideoSearch
import com.example.learnanything.core.ai.youtube.YouTubeSettings
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.EnrichmentRepository
import com.example.learnanything.core.data.repository.LearningRepository
import com.example.learnanything.core.data.repository.RoomAiCacheStore
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.data.usecase.FindSimulationUseCase
import com.example.learnanything.core.data.usecase.FindTopicUseCase
import com.example.learnanything.core.data.usecase.FindVideoUseCase
import com.example.learnanything.core.data.usecase.GetRescueUseCase
import com.example.learnanything.core.data.usecase.OpenLessonUseCase
import com.example.learnanything.core.data.usecase.RescueCheckUseCase
import com.example.learnanything.core.data.usecase.RescueContent
import com.example.learnanything.core.data.usecase.TopicResourcesUseCase
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineTopic
import com.example.learnanything.core.model.question.Answer
import com.example.learnanything.core.model.question.Question
import com.example.learnanything.core.model.rescue.RescueRung
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rescue ladder, videos and simulations against a real Room DB with a fake AI and fake YouTube. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnrichmentFlowTest {
    private class FakeSearch(var hits: List<VideoHit>) : VideoSearch {
        var calls = 0
        override suspend fun search(query: String, key: String): List<VideoHit> { calls++; return hits }
    }

    private class Keys(var key: String?) : YouTubeSettings {
        override suspend fun youTubeKey() = key
    }

    private lateinit var db: AppDatabase
    private val clock = TestClock()
    private lateinit var subjects: RoomSubjectRepository
    private lateinit var learning: LearningRepository
    private lateinit var enrichment: EnrichmentRepository
    private lateinit var ai: FakeAi
    private lateinit var caller: StructuredCaller
    private lateinit var topicId: String
    private val search = FakeSearch(listOf(VideoHit("dQw4w9WgXcQ", "Joins in 3 minutes", "Data Dan")))
    private val keys = Keys(null)
    private var resources = """{"searchQueries":["sql inner join explained"],"phetKey":"","visualDescription":"Two overlapping circles labelled orders and customers."}"""
    private val rescueReplies = ArrayDeque<String>()

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        subjects = RoomSubjectRepository(db, clock)
        learning = LearningRepository(db, clock)
        enrichment = EnrichmentRepository(db, clock)
        ai = FakeAi(mapOf(
            CallType.LESSON to { Fixtures.LESSON },
            CallType.RESOURCES to { resources },
            CallType.RESCUE to { rescueReplies.removeFirst() },
        ))
        caller = StructuredCaller(ProviderRouter(listOf(ai)), JsonSchemaValidator(), RoomAiCacheStore(db.aiCacheDao(), clock), Json { ignoreUnknownKeys = true })
        val subjectId = subjects.saveOutline(
            OutlineRequest("SQL", Level.BEGINNER, 3),
            OutlineResponse("SQL joins", "Learn joins.", listOf(OutlineChapter("Basics", "Write joins", listOf(
                OutlineTopic("Inner joins", listOf("Write one"), emptyList(), 10),
            )))),
        )
        topicId = subjects.outline(subjectId)!!.chapters[0].topics[0].id
        OpenLessonUseCase(FindTopicUseCase(subjects), caller, learning, subjects, clock)(topicId)
    }

    @After fun tearDown() = db.close()

    private fun topicResources() = TopicResourcesUseCase(FindTopicUseCase(subjects), caller)
    private fun findVideo() = FindVideoUseCase(topicResources(), enrichment, search, keys)
    private fun findSim() = FindSimulationUseCase(topicResources(), enrichment)
    private fun rescue() = GetRescueUseCase(FindTopicUseCase(subjects), caller, learning, enrichment, findVideo(), findSim())

    @Test fun withoutAKeyTheVideoRungOffersASearchChip() = runTest {
        val r = findVideo()(topicId) as RescueContent.VideoSearchChip
        assertThat(r.query).isEqualTo("sql inner join explained")
        assertThat(search.calls).isEqualTo(0)
    }

    @Test fun withAKeyTheVideoIsFoundOnceThenReused() = runTest {
        keys.key = "KEY"
        val first = findVideo()(topicId) as RescueContent.Video
        assertThat(first.resource.sourceRef).isEqualTo("dQw4w9WgXcQ")
        val again = findVideo()(topicId) as RescueContent.Video
        assertThat(again.resource.id).isEqualTo(first.resource.id)
        assertThat(search.calls).isEqualTo(1)

        // Thumbs down forgets it, so the next request searches again.
        enrichment.rate(topicId, first.resource, -1)
        findVideo()(topicId)
        assertThat(search.calls).isEqualTo(2)
    }

    @Test fun noFittingSimulationFallsBackToADiagram() = runTest {
        val r = findSim()(topicId) as RescueContent.Diagram
        assertThat(r.description).contains("overlapping circles")
    }

    @Test fun simulationComesFromTheCatalogAndIsStored() = runTest {
        resources = resources.replace("\"phetKey\":\"\"", "\"phetKey\":\"phet:ohms-law\"")
        val sim = findSim()(topicId) as RescueContent.Simulation
        assertThat(sim.sim.url).isEqualTo("https://phet.colorado.edu/sims/html/ohms-law/latest/ohms-law_all.html")
        val calls = ai.calls.size
        assertThat((findSim()(topicId) as RescueContent.Simulation).sim.key).isEqualTo("phet:ohms-law")
        assertThat(ai.calls.size).isEqualTo(calls)
    }

    @Test fun unknownCatalogKeyIsSentBackToTheAi() = runTest {
        var n = 0
        val bad = resources.replace("\"phetKey\":\"\"", "\"phetKey\":\"phet:made-up\"")
        ai = FakeAi(mapOf(CallType.RESOURCES to { if (n++ == 0) bad else resources }))
        caller = StructuredCaller(ProviderRouter(listOf(ai)), JsonSchemaValidator(), RoomAiCacheStore(db.aiCacheDao(), clock), Json { ignoreUnknownKeys = true })
        findSim()(topicId)
        assertThat(ai.calls[1].prompt).contains("not in the catalog")
    }

    @Test fun writtenRungUsesTheProfileAndARightCheckRecordsThatItHelped() = runTest {
        enrichment.saveProfile("Salesforce developer", "SOQL, Apex")
        val analogy = (1..50).joinToString(" ") { "w$it" }
        rescueReplies += """{"title":"Like SOQL relationships","body":"$analogy","blanks":[],"questions":[],"prerequisites":[],"rubric":[]}"""
        val content = rescue()(topicId, RescueRung.ANALOGY) as RescueContent.Written
        assertThat(content.content.title).contains("SOQL")
        assertThat(ai.calls.last().prompt).contains("Learner background: Salesforce developer")

        val check = RescueCheckUseCase(learning, enrichment)
        val item = check.question(topicId, 0)!!
        val right = when (val q = item.question) {
            is Question.SingleChoice -> Answer.Choice(setOf(q.correctIndex))
            is Question.Numeric -> Answer.Number(q.value.toString())
            is Question.ShortText -> Answer.Text(q.accepted.first())
            is Question.MultiChoice -> Answer.Choice(q.correct)
        }
        assertThat(check.answer(topicId, RescueRung.ANALOGY, item, right).correct).isTrue()
        assertThat(enrichment.ladderOrder().first()).isEqualTo(RescueRung.ANALOGY)
    }

    @Test fun skippedRungsDontChangeTheOrder() = runTest {
        RescueCheckUseCase(learning, enrichment).skip(topicId, RescueRung.SIMPLER)
        assertThat(enrichment.ladderOrder()).isEqualTo(RescueRung.entries)
    }
}
