package com.example.learnanything.core.data.db

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class, ChapterEntity::class, TopicEntity::class, AiCacheEntity::class,
        ItemEntity::class, AttemptEntity::class, ConceptEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun learningDao(): LearningDao

    companion object {
        const val NAME = "learn.db"
    }
}
