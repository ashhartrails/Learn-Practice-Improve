package com.example.learnanything.core.model.resources

import com.example.learnanything.core.model.TextSafety
import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.ai.CallType
import com.example.learnanything.core.model.ai.PromptSpec
import kotlinx.serialization.Serializable

/**
 * Principle 3: the AI never supplies URLs. It returns YouTube search queries and, optionally,
 * one PhET catalog key; the app searches and builds every URL itself.
 */
@Serializable
data class ResourcesResponse(
    val searchQueries: List<String>,
    /** A key from [PhetCatalog], or "" when no simulation fits. */
    val phetKey: String,
    /** A text description of a diagram that would help, used when no simulation fits. */
    val visualDescription: String,
)

data class ResourcesContext(val subjectTitle: String, val topicTitle: String, val objectives: List<String>, val levelLabel: String)

object ResourcesRules {
    fun check(r: ResourcesResponse): List<String> {
        val e = mutableListOf<String>()
        if (r.searchQueries.isEmpty()) e += "give 1 to 3 search queries"
        r.searchQueries.forEachIndexed { i, q ->
            if (q.isBlank() || q.length > 80) e += "searchQueries[$i] must be 1 to 80 characters"
        }
        if (r.phetKey.isNotBlank() && PhetCatalog.find(r.phetKey) == null) {
            e += "phetKey \"${r.phetKey}\" is not in the catalog; use an exact key from the list or \"\""
        }
        e += TextSafety.urlProblems(r.searchQueries.mapIndexed { i, q -> "searchQueries[$i]" to q } + ("visualDescription" to r.visualDescription))
        return e
    }
}

object ResourcesPrompt {
    const val VERSION = 1

    private val SYSTEM = """
        You find learning resources for one topic. Never write URLs or links.
        - searchQueries: 1 to 3 YouTube search queries (each under 80 characters) that would find
          a short, clear explainer video for this exact topic at the learner's level.
        - phetKey: if one of the PhET simulations below directly shows this topic, its exact key;
          otherwise "". Don't force a weak match.
        - visualDescription: 2 to 5 sentences describing a simple diagram that would make the
          idea click (what to draw, what to label, what changes).
        PhET catalog (key: title (area)):
    """.trimIndent()

    fun build(ctx: ResourcesContext): PromptSpec = PromptSpec(
        callType = CallType.RESOURCES,
        version = VERSION,
        system = SYSTEM + "\n" + PhetCatalog.promptList(),
        user = buildString {
            appendLine("Course: ${ctx.subjectTitle} (learner level: ${ctx.levelLabel})")
            appendLine("Topic: ${ctx.topicTitle}")
            append("Objectives: ${ctx.objectives.joinToString("; ")}")
        },
        schema = AiSchema.RESOURCES_V1,
    )
}
