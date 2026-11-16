package com.example.learnanything.core.model.outline

import com.example.learnanything.core.model.Level
import kotlinx.serialization.Serializable

/** What the learner asked for. */
data class OutlineRequest(
    val goal: String,
    val level: Level,
    val weeklyHours: Int,
)

/** The `outline` call's response. Must match `schemas/outline.v1.json`. */
@Serializable
data class OutlineResponse(
    val title: String,
    val summary: String,
    val chapters: List<OutlineChapter>,
)

@Serializable
data class OutlineChapter(
    val title: String,
    val goal: String,
    val topics: List<OutlineTopic>,
)

@Serializable
data class OutlineTopic(
    val title: String,
    val objectives: List<String>,
    val prerequisites: List<String>,
    val estimatedMinutes: Int,
)
