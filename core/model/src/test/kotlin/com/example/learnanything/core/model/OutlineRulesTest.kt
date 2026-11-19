package com.example.learnanything.core.model

import com.example.learnanything.core.model.ai.AiSchema
import com.example.learnanything.core.model.outline.OutlineChapter
import com.example.learnanything.core.model.outline.OutlineResponse
import com.example.learnanything.core.model.outline.OutlineRules
import com.example.learnanything.core.model.outline.OutlineTopic
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutlineRulesTest {
    private fun topic(title: String) = OutlineTopic(title, listOf("Do a thing"), emptyList(), 10)

    @Test fun validOutlineHasNoProblems() {
        val outline = OutlineResponse(
            "SQL joins", "Learn to combine tables.",
            listOf(OutlineChapter("Basics", "Write simple joins", listOf(topic("Inner join"), topic("Left join")))),
        )
        assertThat(OutlineRules.check(outline)).isEmpty()
    }

    @Test fun duplicateTopicTitlesAcrossChaptersAreRejected() {
        val outline = OutlineResponse(
            "SQL", "Learn SQL joins well.",
            listOf(
                OutlineChapter("One", "Goal one here", listOf(topic("Joins"))),
                OutlineChapter("Two", "Goal two here", listOf(topic(" joins "))),
            ),
        )
        assertThat(OutlineRules.check(outline).single()).contains("duplicate topic title")
    }

    @Test fun duplicateChapterTitlesAreRejected() {
        val outline = OutlineResponse(
            "SQL", "Learn SQL joins well.",
            listOf(
                OutlineChapter("Basics", "Goal one here", listOf(topic("A1"))),
                OutlineChapter("BASICS", "Goal two here", listOf(topic("B1"))),
            ),
        )
        assertThat(OutlineRules.check(outline).single()).contains("duplicate chapter title")
    }

    @Test fun schemaResourceLoadsAndHasStableId() {
        assertThat(AiSchema.OUTLINE_V1.load()).contains("\"chapters\"")
        assertThat(AiSchema.OUTLINE_V1.id).isEqualTo("outline.v1")
    }
}
