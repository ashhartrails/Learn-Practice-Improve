package com.example.learnanything.core.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [SubjectEntity::class, ChapterEntity::class, TopicEntity::class, AiCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun aiCacheDao(): AiCacheDao

    companion object {
        const val NAME = "learn.db"
    }
}
