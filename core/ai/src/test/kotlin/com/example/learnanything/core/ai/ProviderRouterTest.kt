package com.example.learnanything.core.ai

import com.example.learnanything.core.model.ai.Tier
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProviderRouterTest {
    private class P(override val id: String, override val kind: ProviderKind, private val configured: Boolean = true) : AiProvider {
        override val displayName = id
        override suspend fun isConfigured() = configured
        override suspend fun listModels() = emptyList<ModelInfo>()
        override fun generate(request: AiRequest): Flow<AiChunk> = emptyFlow()
    }

    private val cloudA = P("cloudA", ProviderKind.CLOUD)
    private val local = P("local", ProviderKind.LOCAL)
    private val cloudB = P("cloudB", ProviderKind.CLOUD)
    private val unconfigured = P("off", ProviderKind.CLOUD, configured = false)

    @Test fun strongTierNeverUsesLocalModels() = runTest {
        val ids = ProviderRouter(listOf(cloudA, local, cloudB)).providersFor(Tier.STRONG).map { it.id }
        assertThat(ids).containsExactly("cloudA", "cloudB").inOrder()
    }

    @Test fun smallAndMediumTiersMayUseLocalModels() = runTest {
        val router = ProviderRouter(listOf(cloudA, local))
        assertThat(router.providersFor(Tier.MEDIUM).map { it.id }).containsExactly("cloudA", "local").inOrder()
        assertThat(router.providersFor(Tier.SMALL).map { it.id }).contains("local")
    }

    @Test fun unconfiguredProvidersAreSkipped() = runTest {
        assertThat(ProviderRouter(listOf(unconfigured, cloudA)).providersFor(Tier.MEDIUM).map { it.id }).containsExactly("cloudA")
    }

    @Test fun fallbackOrderIsRegistrationOrder() = runTest {
        assertThat(ProviderRouter(listOf(cloudB, cloudA)).providersFor(Tier.STRONG).map { it.id }).containsExactly("cloudB", "cloudA").inOrder()
    }
}
