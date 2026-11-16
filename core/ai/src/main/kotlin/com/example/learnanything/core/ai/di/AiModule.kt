package com.example.learnanything.core.ai.di

import com.example.learnanything.core.ai.AiProvider
import com.example.learnanything.core.ai.gemini.GeminiProvider
import com.example.learnanything.core.ai.gemini.GeminiSettings
import com.example.learnanything.core.ai.validation.JsonSchemaValidator
import com.example.learnanything.core.ai.validation.SchemaValidator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindings {
    @Binds abstract fun schemaValidator(impl: JsonSchemaValidator): SchemaValidator
}

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Streaming responses can pause while the model thinks.
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun gemini(http: OkHttpClient, settings: GeminiSettings, json: Json): GeminiProvider =
        GeminiProvider(http, settings, json)

    /** Fallback order. M0 has one provider; later milestones read the learner's order from settings. */
    @Provides @Singleton
    fun providers(gemini: GeminiProvider): List<@JvmSuppressWildcards AiProvider> = listOf(gemini)
}
