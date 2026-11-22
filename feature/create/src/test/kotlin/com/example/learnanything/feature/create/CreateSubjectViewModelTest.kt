package com.example.learnanything.feature.create

import com.example.learnanything.core.ai.AiCacheStore
import com.example.learnanything.core.ai.AiChunk
import com.example.learnanything.core.ai.AiException
import com.example.learnanything.core.ai.AiProvider
import com.example.learnanything.core.ai.AiRequest
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.ai.ProviderKind
import com.example.learnanything.core.ai.ProviderRouter
import com.example.learnanything.core.ai.StructuredCaller
import com.example.learnanything.core.ai.validation.JsonSchemaValidator
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.data.usecase.ClarifyGoalUseCase
import com.example.learnanything.core.data.usecase.GenerateOutlineUseCase
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.SubjectOutline
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.clarify.Clarification
import com.example.learnanything.core.model.outline.OutlineRequest
import com.example.learnanything.core.model.outline.OutlineResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateSubjectViewModelTest {
    private val outlineJson = """
        {"title":"SQL joins","summary":"Combine tables with joins.","chapters":[
          {"title":"Basics","goal":"Write a two-table join.","topics":[
            {"title":"Inner join","objectives":["Write an INNER JOIN"],"prerequisites":[],"estimatedMinutes":15}
          ]}
        ]}
    """.trimIndent()
    private val clarifyJson = """
        {"questions":[
          {"id":"why","question":"Why are you learning SQL?","kind":"choice","options":["Work","Study","Fun"]},
          {"id":"db","question":"Which database do you use?","kind":"text","options":[]}
        ]}
    """.trimIndent()

    private data class Saved(val request: OutlineRequest, val outline: OutlineResponse, val clarifications: List<Clarification>, val draft: Boolean)
    private val saved = mutableListOf<Saved>()

    private val repo = object : SubjectRepository {
        override fun observeSubjects(): Flow<List<Subject>> = emptyFlow()
        override fun observeOutline(subjectId: String): Flow<SubjectOutline?> = emptyFlow()
        override suspend fun outline(subjectId: String): SubjectOutline? = null
        override suspend fun saveOutline(request: OutlineRequest, outline: OutlineResponse, clarifications: List<Clarification>, draft: Boolean): String {
            saved += Saved(request, outline, clarifications, draft)
            return "subject-1"
        }
        override suspend fun delete(subjectId: String) = Unit
        override suspend fun activate(subjectId: String) = Unit
        override suspend fun renameChapter(chapterId: String, title: String) = Unit
        override suspend fun renameTopic(topicId: String, title: String) = Unit
        override suspend fun moveChapter(chapterId: String, up: Boolean) = Unit
        override suspend fun moveTopic(topicId: String, up: Boolean) = Unit
        override suspend fun deleteChapter(chapterId: String) = Unit
        override suspend fun deleteTopic(topicId: String) = Unit
        override suspend fun addTopic(chapterId: String, title: String) = Unit
        override suspend fun touch(subjectId: String) = Unit
    }

    private val noCache = object : AiCacheStore {
        override suspend fun get(promptHash: String): String? = null
        override suspend fun put(promptHash: String, callType: CallType, response: String) = Unit
    }

    private class Fake(private val configured: Boolean = true, private val reply: (CallType) -> String) : AiProvider {
        override val id = "fake"
        override val displayName = "Fake"
        override val kind = ProviderKind.CLOUD
        val requests = mutableListOf<AiRequest>()
        override suspend fun isConfigured() = configured
        override suspend fun listModels() = listOf(ModelInfo("m", "m"))
        override fun generate(request: AiRequest): Flow<AiChunk> = flow {
            requests += request
            emit(AiChunk.Text(reply(request.callType)))
            emit(AiChunk.Done("m", "STOP"))
        }
    }

    private fun viewModel(provider: AiProvider): CreateSubjectViewModel {
        val caller = StructuredCaller(ProviderRouter(listOf(provider)), JsonSchemaValidator(), noCache, Json { ignoreUnknownKeys = true })
        return CreateSubjectViewModel(ClarifyGoalUseCase(caller), GenerateOutlineUseCase(caller, repo))
    }

    private val happy = Fake { if (it == CallType.CLARIFY) clarifyJson else outlineJson }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun shortGoalCannotContinue() {
        val vm = viewModel(happy)
        vm.onGoalChange("ab")
        assertThat(vm.state.value.canContinue).isFalse()
    }

    @Test fun goalThenQuestionsThenDraftOutlineWithAnswers() = runTest {
        val vm = viewModel(happy)
        vm.onGoalChange("SQL joins")
        vm.onLevelChange(Level.INTERMEDIATE)
        vm.askQuestions()
        assertThat(vm.state.value.step).isEqualTo(CreateStep.CLARIFY)
        assertThat(vm.state.value.questions.map { it.id }).containsExactly("why", "db").inOrder()

        vm.onAnswer("why", "Work")
        vm.onAnswer("db", "  ")  // blank answers are skipped
        vm.buildCourse()

        assertThat(vm.state.value.createdSubjectId).isEqualTo("subject-1")
        val s = saved.single()
        assertThat(s.draft).isTrue()
        assertThat(s.request.level).isEqualTo(Level.INTERMEDIATE)
        assertThat(s.clarifications).containsExactly(Clarification("Why are you learning SQL?", "Work"))
        assertThat(happy.requests.last().prompt).contains("Work")
    }

    @Test fun clarifyRateLimitOffersBuildingWithoutQuestions() = runTest {
        val provider = Fake { if (it == CallType.CLARIFY) throw AiException.RateLimited("fake", 42, "quota") else outlineJson }
        val vm = viewModel(provider)
        vm.onGoalChange("SQL joins")
        vm.askQuestions()

        assertThat(vm.state.value.step).isEqualTo(CreateStep.GOAL)
        assertThat(vm.state.value.error!!.text).contains("42 seconds")
        assertThat(vm.state.value.canSkipQuestions).isTrue()

        vm.skipQuestions()
        assertThat(vm.state.value.createdSubjectId).isEqualTo("subject-1")
        assertThat(saved.single().clarifications).isEmpty()
    }

    @Test fun missingKeyPointsToSettingsAndCannotSkip() = runTest {
        val vm = viewModel(Fake(configured = false) { outlineJson })
        vm.onGoalChange("SQL joins")
        vm.askQuestions()

        assertThat(vm.state.value.error!!.needsSettings).isTrue()
        assertThat(vm.state.value.canSkipQuestions).isFalse()
        assertThat(saved).isEmpty()
    }

    @Test fun invalidOutlineTwiceReturnsToQuestionsAndSavesNothing() = runTest {
        val vm = viewModel(Fake { if (it == CallType.CLARIFY) clarifyJson else """{"title":"x"}""" })
        vm.onGoalChange("SQL joins")
        vm.askQuestions()
        vm.buildCourse()

        assertThat(vm.state.value.step).isEqualTo(CreateStep.CLARIFY)
        assertThat(vm.state.value.error!!.text).contains("didn't pass our checks")
        assertThat(saved).isEmpty()
    }
}
