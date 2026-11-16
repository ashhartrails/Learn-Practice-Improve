package com.example.learnanything.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.example.learnanything.core.ai.AiCacheStore
import com.example.learnanything.core.ai.gemini.GeminiSettings
import com.example.learnanything.core.data.SystemTimeSource
import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.db.AiCacheDao
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.repository.RoomAiCacheStore
import com.example.learnanything.core.data.repository.RoomSubjectRepository
import com.example.learnanything.core.data.repository.SubjectRepository
import com.example.learnanything.core.data.settings.KeystoreSecretCipher
import com.example.learnanything.core.data.settings.SecretCipher
import com.example.learnanything.core.data.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds abstract fun subjects(impl: RoomSubjectRepository): SubjectRepository
    @Binds abstract fun aiCache(impl: RoomAiCacheStore): AiCacheStore
    @Binds abstract fun geminiSettings(impl: SettingsRepository): GeminiSettings
    @Binds abstract fun secretCipher(impl: KeystoreSecretCipher): SecretCipher
    @Binds abstract fun timeSource(impl: SystemTimeSource): TimeSource
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder<AppDatabase>(context, AppDatabase.NAME)
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    @Provides fun aiCacheDao(db: AppDatabase): AiCacheDao = db.aiCacheDao()

    @Provides @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
