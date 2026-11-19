package com.example.learnanything.core.ai.youtube

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** The learner's own YouTube Data API key, if they added one. */
interface YouTubeSettings {
    suspend fun youTubeKey(): String?
}

data class VideoHit(val videoId: String, val title: String, val channel: String)

sealed class VideoSearchException(message: String) : Exception(message) {
    class BadKey : VideoSearchException("YouTube rejected the API key")
    class Quota : VideoSearchException("The YouTube key's daily quota is used up")
    class Network(cause: IOException) : VideoSearchException("Could not reach YouTube: ${cause.message}")
    class Server(code: Int) : VideoSearchException("YouTube error $code")
}

interface VideoSearch {
    /** Embeddable, short, safe-search videos for [query]. */
    suspend fun search(query: String, key: String): List<VideoHit>
}

/** YouTube Data API v3 `search.list`. Costs 100 quota units per call; results are cached per topic. */
@Singleton
class YouTubeVideoSearch @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) : VideoSearch {
    private var base = "https://www.googleapis.com/youtube/v3/search"

    internal constructor(http: OkHttpClient, json: Json, baseUrl: String) : this(http, json) { base = baseUrl }

    override suspend fun search(query: String, key: String): List<VideoHit> = withContext(Dispatchers.IO) {
        // Short (under 4 minutes) first; medium (4 to 20) if nothing short fits.
        val short = request(query, key, "short")
        short.ifEmpty { request(query, key, "medium") }
    }

    private fun request(query: String, key: String, duration: String): List<VideoHit> {
        val url = base.toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("type", "video")
            .addQueryParameter("videoEmbeddable", "true")
            .addQueryParameter("videoSyndicated", "true")
            .addQueryParameter("safeSearch", "strict")
            .addQueryParameter("videoDuration", duration)
            .addQueryParameter("relevanceLanguage", "en")
            .addQueryParameter("maxResults", "5")
            .addQueryParameter("q", query)
            .addQueryParameter("key", key)
            .build()
        val response = try {
            http.newCall(Request.Builder().url(url).get().build()).execute()
        } catch (e: IOException) {
            throw VideoSearchException.Network(e)
        }
        response.use {
            val body = it.body.string()
            if (!it.isSuccessful) {
                throw when {
                    it.code == 403 && body.contains("quota", ignoreCase = true) -> VideoSearchException.Quota()
                    it.code == 400 || it.code == 403 -> VideoSearchException.BadKey()
                    else -> VideoSearchException.Server(it.code)
                }
            }
            return parse(json, body)
        }
    }

    companion object {
        private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

        /** Keeps only well-formed video ids; the app builds the embed URL from the id alone. */
        fun parse(json: Json, body: String): List<VideoHit> =
            json.decodeFromString(SearchResponse.serializer(), body).items.mapNotNull { item ->
                val id = item.id.videoId ?: return@mapNotNull null
                if (!VIDEO_ID.matches(id)) return@mapNotNull null
                VideoHit(id, item.snippet?.title.orEmpty(), item.snippet?.channelTitle.orEmpty())
            }
    }
}

@Serializable private data class SearchResponse(val items: List<SearchItem> = emptyList())
@Serializable private data class SearchItem(val id: ItemId, val snippet: Snippet? = null)
@Serializable private data class ItemId(val videoId: String? = null)
@Serializable private data class Snippet(val title: String = "", val channelTitle: String = "")
