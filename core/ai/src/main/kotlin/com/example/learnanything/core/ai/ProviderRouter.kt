package com.example.learnanything.core.ai

import com.example.learnanything.core.model.ai.Tier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks providers for a call, in fallback order.
 * M0: cloud providers only, in registration order. M5 adds on-device routing and
 * the learner's own fallback order. STRONG calls never go to a LOCAL provider.
 */
@Singleton
class ProviderRouter @Inject constructor(
    private val providers: List<@JvmSuppressWildcards AiProvider>,
) {
    suspend fun providersFor(tier: Tier): List<AiProvider> =
        providers
            .filter { tier != Tier.STRONG || it.kind == ProviderKind.CLOUD }
            .filter { it.isConfigured() }
}
