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
import com.example.learnanything.core.data.usecase.GenerateOutlineUseCase
import com.example.learnanything.core.model.Level
import com.example.learnanything.core.model.Subject
import com.example.learnanything.core.model.SubjectOutline
import com.example.learnanything.core.model.ai.CallType
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
    private val validOutline = """
        {"title":"SQL joins","summary":"Combine tables with joins.","chapters":[
          {"title":"Basics","goal":"Write a two-table join.","topics":[
            {"title":"Inner join","objectives":["Write an INNER JOIN"],"prerequisites":[],"estimatedMinutes":15}
          ]}
        ]}
    """.trimIndent()

    private val saved = mutableListOf<Pair<OutlineRequest, OutlineResponse>>()

    private val repo = object : SubjectRepository {
        override fun observeSubjects(): Flow<List<Subject>> = emptyFlow()
        override fun observeOutline(subjectId: String): Flow<SubjectOutline?> = emptyFlow()
        override suspend fun saveOutline(request: OutlineRequest, outline: OutlineResponse): String {
            saved += request to outline
            return "subject-1"
        }
        override suspend fun delete(subjectId: String) = Unit
    }

    private val noCache = object : AiCacheStore {
        override suspend fun get(promptHash: String): String? = null
        override suspend fun put(promptHash: String, callType: CallType, response: String) = Unit
    }

    private class OneShotProvider(private val reply: () -> String, private val configured: Boolean = true) : AiProvider {
        override val id = "fake"
        override val displayName = "Fake"
        override val kind = ProviderKind.CLOUD
        override suspend fun isConfigured() = configured
        override suspend fun listModels() = listOf(ModelInfo("m", "m"))
        override fun generate(request: AiRequest): Flow<AiChunk> = flow {
            emit(AiChunk.Text(reply()))
            emit(AiChunk.Done("m", "STOP"))
        }
    }

    private fun viewModel(provider: AiProvider): CreateSubjectViewModel {
        val json = Json { ignoreUnknownKeys = true }
        val caller = StructuredCaller(ProviderRouter(listOf(provider)), JsonSchemaValidator(), noCache, json)
        return CreateSubjectViewModel(GenerateOutlineUseCase(caller, repo))
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun shortGoalCannotBeSubmitted() {
        val vm = viewModel(OneShotProvider({ validOutline }))
        vm.onGoalChange("ab")
        assertThat(vm.state.value.canSubmit).isFalse()
    }

    @Test fun successSavesOutlineAndExposesSubjectId() = runTest {
        val vm = viewModel(OneShotProvider({ validOutline }))
        vm.onGoalChange("SQL joins")
        vm.onLevelChange(Level.INTERMEDIATE)
        vm.generate()

        assertThat(vm.state.value.createdSubjectId).isEqualTo("subject-1")
        assertThat(saved.single().first.level).isEqualTo(Level.INTERMEDIATE)
        assertThat(saved.single().second.title).isEqualTo("SQL joins")
    }

    @Test fun missingKeyPointsToSettings() = runTest {
        val vm = viewModel(OneShotProvider({ validOutline }, configured = false))
        vm.onGoalChange("SQL joins")
        vm.generate()

        val error = vm.state.value.error!!
        assertThat(error.needsSettings).isTrue()
        assertThat(saved).isEmpty()
    }

    @Test fun rateLimitShowsRetryTimeAndSavesNothing() = runTest {
        val vm = viewModel(OneShotProvider({ throw AiException.RateLimited("fake", 42, "quota") }))
        vm.onGoalChange("SQL joins")
        vm.generate()

        assertThat(vm.state.value.error!!.message).contains("42 seconds")
        assertThat(vm.state.value.generating).isFalse()
        assertThat(saved).isEmpty()
    }

    @Test fun invalidOutputTwiceIsAnErrorAndSavesNothing() = runTest {
        val vm = viewModel(OneShotProvider({ """{"title":"x"}""" }))
        vm.onGoalChange("SQL joins")
        vm.generate()

        assertThat(vm.state.value.error!!.message).contains("didn't pass our checks")
        assertThat(saved).isEmpty()
    }
}
