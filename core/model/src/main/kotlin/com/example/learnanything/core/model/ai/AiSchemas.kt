package com.example.learnanything.core.model.ai

/**
 * JSON schemas for AI responses, stored as resources under `schemas/`.
 * The same schema is sent to the provider (as a hint) and enforced locally (as the gate).
 */
enum class AiSchema(val resourcePath: String) {
    OUTLINE_V1("schemas/outline.v1.json"),
    CLARIFY_V1("schemas/clarify.v1.json"),
    LESSON_V1("schemas/lesson.v1.json"),
    QUIZ_V1("schemas/quiz.v1.json"),
    PRACTICAL_V1("schemas/practical.v1.json"),
    GRADE_V1("schemas/grade.v1.json"),
    RESCUE_V1("schemas/rescue.v1.json"),
    RESOURCES_V1("schemas/resources.v1.json");

    /** Stable identifier included in cache keys, e.g. `outline.v1`. */
    val id: String get() = resourcePath.substringAfterLast('/').removeSuffix(".json")

    fun load(): String {
        val stream = AiSchema::class.java.classLoader?.getResourceAsStream(resourcePath)
            ?: error("Schema resource missing: $resourcePath")
        return stream.bufferedReader().use { it.readText() }
    }
}
