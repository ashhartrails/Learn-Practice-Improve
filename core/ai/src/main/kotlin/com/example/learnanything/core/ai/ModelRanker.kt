package com.example.learnanything.core.ai

import com.example.learnanything.core.model.ai.Tier

/**
 * Orders a provider's live model list so the app can pre-select a sensible default.
 * This uses naming traits (preview, flash, lite), never fixed model names, so it keeps
 * working when providers release or retire models. The learner can always override it.
 */
object ModelRanker {
    private val NON_TEXT_MARKERS = listOf(
        "embedding", "embed", "imagen", "image", "tts", "audio", "live", "veo", "aqa", "robotics",
    )

    fun isTextModel(model: ModelInfo): Boolean {
        val id = model.id.lowercase()
        return NON_TEXT_MARKERS.none { it in id }
    }

    fun rank(models: List<ModelInfo>, tier: Tier = Tier.STRONG): List<ModelInfo> =
        models.filter(::isTextModel).sortedWith(
            compareByDescending<ModelInfo> { score(it, tier) }
                .thenByDescending { it.outputTokenLimit ?: 0 }
                .thenByDescending { it.id },
        )

    fun pick(models: List<ModelInfo>, tier: Tier = Tier.STRONG): ModelInfo? = rank(models, tier).firstOrNull()

    private fun score(model: ModelInfo, tier: Tier): Int {
        val id = model.id.lowercase()
        var score = 0
        if (model.isPreview) score -= 100
        if ("flash" in id) score += 20
        if ("pro" in id) score += 5
        if ("lite" in id) score += if (tier == Tier.STRONG) -10 else 5
        if ("latest" in id) score += 3
        return score
    }
}
