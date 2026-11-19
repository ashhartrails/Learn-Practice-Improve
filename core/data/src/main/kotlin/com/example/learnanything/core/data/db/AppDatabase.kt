package com.example.learnanything.core.data.db

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class, ChapterEntity::class, TopicEntity::class, AiCacheEntity::class,
        ItemEntity::class, AttemptEntity::class, ConceptEntity::class,
        ResourceEntity::class, RescueEventEntity::class, LearnerProfileEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun learningDao(): LearningDao
    abstract fun enrichmentDao(): EnrichmentDao

    companion object {
        const val NAME = "learn.db"
    }
}
