package com.example.learnanything.core.ai.gemini

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class ModelsPageDto(
    val models: List<ModelDto> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
internal data class ModelDto(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
)

@Serializable
internal data class StreamChunkDto(
    val candidates: List<CandidateDto> = emptyList(),
    val promptFeedback: PromptFeedbackDto? = null,
    val modelVersion: String? = null,
)

@Serializable
internal data class CandidateDto(
    val content: ContentDto? = null,
    val finishReason: String? = null,
)

@Serializable
internal data class ContentDto(val parts: List<PartDto> = emptyList())

@Serializable
internal data class PartDto(val text: String? = null, val thought: Boolean? = null)

@Serializable
internal data class PromptFeedbackDto(val blockReason: String? = null)

@Serializable
internal data class ErrorEnvelopeDto(val error: ErrorDto? = null)

@Serializable
internal data class ErrorDto(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null,
    val details: List<JsonObject> = emptyList(),
)
