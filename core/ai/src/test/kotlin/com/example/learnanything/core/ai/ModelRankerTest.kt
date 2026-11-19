package com.example.learnanything.core.ai

import com.example.learnanything.core.model.ai.Tier
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelRankerTest {
    // Invented names on purpose: the ranker must work on traits, not known models.
    private val models = listOf(
        ModelInfo("text-embedding-x", "Embedding"),
        ModelInfo("nova-pro-preview", "Nova Pro Preview", isPreview = true),
        ModelInfo("nova-flash-lite", "Nova Flash-Lite"),
        ModelInfo("nova-flash", "Nova Flash"),
        ModelInfo("nova-image", "Nova Image"),
    )

    @Test fun strongTierPrefersStableFlashOverLiteAndPreview() {
        assertThat(ModelRanker.pick(models, Tier.STRONG)?.id).isEqualTo("nova-flash")
    }

    @Test fun nonTextModelsAreExcluded() {
        val ids = ModelRanker.rank(models).map { it.id }
        assertThat(ids).containsNoneOf("text-embedding-x", "nova-image")
    }

    @Test fun previewRanksLast() {
        assertThat(ModelRanker.rank(models).last().id).isEqualTo("nova-pro-preview")
    }

    @Test fun unknownNamesStillProduceAPick() {
        val pick = ModelRanker.pick(listOf(ModelInfo("hydrogen", "Hydrogen", outputTokenLimit = 8000)))
        assertThat(pick?.id).isEqualTo("hydrogen")
    }

    @Test fun emptyListPicksNothing() {
        assertThat(ModelRanker.pick(emptyList())).isNull()
    }
}
