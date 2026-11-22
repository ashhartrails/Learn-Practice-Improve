package com.example.learnanything.core.ai.youtube

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class YouTubeVideoSearchTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var search: YouTubeVideoSearch

    @Before fun setUp() {
        server.start()
        search = YouTubeVideoSearch(OkHttpClient(), json, server.url("/youtube/v3/search").toString())
    }

    @After fun tearDown() = server.close()

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private val results = """{"items":[
        {"id":{"kind":"youtube#channel","channelId":"UC1"}},
        {"id":{"kind":"youtube#video","videoId":"bad id <script>"},"snippet":{"title":"Evil"}},
        {"id":{"kind":"youtube#video","videoId":"dQw4w9WgXcQ"},"snippet":{"title":"SQL joins in 3 minutes","channelTitle":"Data Dan"}}]}"""

    @Test fun sendsFiltersAndKeepsOnlyValidVideoIds() = runTest {
        server.enqueue(ok(results))
        val hits = search.search("sql inner join explained", "KEY")
        assertThat(hits).containsExactly(VideoHit("dQw4w9WgXcQ", "SQL joins in 3 minutes", "Data Dan"))

        val url = server.takeRequest().url
        assertThat(url.queryParameter("videoEmbeddable")).isEqualTo("true")
        assertThat(url.queryParameter("type")).isEqualTo("video")
        assertThat(url.queryParameter("safeSearch")).isEqualTo("strict")
        assertThat(url.queryParameter("videoDuration")).isEqualTo("short")
        assertThat(url.queryParameter("q")).isEqualTo("sql inner join explained")
        assertThat(url.queryParameter("key")).isEqualTo("KEY")
    }

    @Test fun fallsBackToMediumLengthWhenNoShortVideo() = runTest {
        server.enqueue(ok("""{"items":[]}"""))
        server.enqueue(ok(results))
        assertThat(search.search("q", "KEY")).hasSize(1)
        server.takeRequest()
        assertThat(server.takeRequest().url.queryParameter("videoDuration")).isEqualTo("medium")
    }

    @Test fun quotaAndBadKeyAreDistinguished() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("""{"error":{"errors":[{"reason":"quotaExceeded"}]}}""").build())
        assertThrows(VideoSearchException.Quota::class.java) { kotlinx.coroutines.runBlocking { search.search("q", "KEY") } }
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":{"errors":[{"reason":"keyInvalid"}]}}""").build())
        assertThrows(VideoSearchException.BadKey::class.java) { kotlinx.coroutines.runBlocking { search.search("q", "KEY") } }
    }
}
