package com.example.learnanything.core.model.outline

/**
 * Checks the schema cannot express. Returns human-readable problems; empty means OK.
 * These messages are fed back to the model on retry, so keep them specific.
 */
object OutlineRules {
    const val MAX_TOTAL_TOPICS = 120

    fun check(outline: OutlineResponse): List<String> {
        val problems = mutableListOf<String>()
        if (outline.title.isBlank()) problems += "title is blank"

        val chapterTitles = outline.chapters.map { it.title.trim().lowercase() }
        chapterTitles.groupBy { it }.filter { it.value.size > 1 }.keys.forEach {
            problems += "duplicate chapter title \"$it\""
        }

        val allTopicTitles = mutableListOf<String>()
        outline.chapters.forEachIndexed { ci, chapter ->
            if (chapter.title.isBlank()) problems += "chapters[$ci].title is blank"
            chapter.topics.forEachIndexed { ti, topic ->
                if (topic.title.isBlank()) problems += "chapters[$ci].topics[$ti].title is blank"
                if (topic.objectives.any { it.isBlank() }) {
                    problems += "chapters[$ci].topics[$ti].objectives contains a blank entry"
                }
                allTopicTitles += topic.title.trim().lowercase()
            }
        }
        allTopicTitles.groupBy { it }.filter { it.value.size > 1 }.keys.forEach {
            problems += "duplicate topic title \"$it\" (topic titles must be unique across the course)"
        }
        if (allTopicTitles.size > MAX_TOTAL_TOPICS) {
            problems += "too many topics (${allTopicTitles.size}); the limit is $MAX_TOTAL_TOPICS"
        }
        return problems
    }
}
