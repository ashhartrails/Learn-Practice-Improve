package com.example.learnanything.core.model

/** Domain types used by screens. Room entities are mapped to these in core/data. */
data class Subject(
    val id: String,
    val title: String,
    val goal: String,
    val level: Level,
    val weeklyHours: Int,
    val summary: String,
    val status: SubjectStatus,
    val updatedAt: Long,
)

enum class SubjectStatus { ACTIVE, ARCHIVED }

data class Chapter(
    val id: String,
    val order: Int,
    val title: String,
    val goal: String,
    val topics: List<Topic>,
)

data class Topic(
    val id: String,
    val order: Int,
    val title: String,
    val objectives: List<String>,
    val prerequisites: List<String>,
    val estimatedMinutes: Int,
)

data class SubjectOutline(val subject: Subject, val chapters: List<Chapter>)
