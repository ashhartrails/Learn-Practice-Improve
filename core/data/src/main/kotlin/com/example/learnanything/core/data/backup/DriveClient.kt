package com.example.learnanything.core.data.backup

import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class Snapshot(val id: String, val name: String, val createdAt: Long, val sizeBytes: Long?)

sealed class DriveException(message: String) : IOException(message) {
    class Unauthorized : DriveException("Google Drive access expired or was removed. Turn backup on again in Settings.")
    class Network(cause: IOException) : DriveException("Couldn't reach Google Drive: ${cause.message}")
    class Server(code: Int, detail: String) : DriveException("Google Drive error $code: ${detail.take(200)}")
}

/**
 * Google Drive REST v3, limited to the app's hidden appDataFolder (scope `drive.appdata`).
 * The learner's own Drive files are never visible to the app.
 */
@Singleton
class DriveClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
) {
    private var base: HttpUrl = "https://www.googleapis.com/".toHttpUrl()

    internal constructor(http: OkHttpClient, json: Json, baseUrl: HttpUrl) : this(http, json) { base = baseUrl }

    suspend fun list(token: String): List<Snapshot> = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegments("drive/v3/files")
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name contains '$PREFIX' and trashed = false")
            .addQueryParameter("fields", "files(id,name,createdTime,size)")
            .addQueryParameter("orderBy", "createdTime desc")
            .addQueryParameter("pageSize", "100")
            .build()
        val body = call(Request.Builder().url(url).get(), token)
        json.decodeFromString(FileList.serializer(), body).files.map { it.toSnapshot() }
    }

    suspend fun upload(token: String, name: String, bytes: ByteArray): Snapshot = withContext(Dispatchers.IO) {
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", ZIP)
            putJsonArray("parents") { add("appDataFolder") }
            putJsonObject("appProperties") { put("format", "learn-backup") }
        }.toString()
        val multipart = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(bytes.toRequestBody(ZIP.toMediaType()))
            .build()
        val url = base.newBuilder().addPathSegments("upload/drive/v3/files")
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,createdTime,size")
            .build()
        json.decodeFromString(DriveFile.serializer(), call(Request.Builder().url(url).post(multipart), token)).toSnapshot()
    }

    suspend fun download(token: String, id: String): ByteArray = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegments("drive/v3/files").addPathSegment(id).addQueryParameter("alt", "media").build()
        execute(Request.Builder().url(url).get(), token) { it.body.bytes() }
    }

    suspend fun delete(token: String, id: String) = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegments("drive/v3/files").addPathSegment(id).build()
        execute(Request.Builder().url(url).delete(), token) { }
    }

    private fun call(builder: Request.Builder, token: String): String = execute(builder, token) { it.body.string() }

    private fun <T> execute(builder: Request.Builder, token: String, read: (okhttp3.Response) -> T): T {
        val response = try {
            http.newCall(builder.header("Authorization", "Bearer $token").build()).execute()
        } catch (e: IOException) {
            throw DriveException.Network(e)
        }
        return response.use {
            when {
                it.code == 401 || it.code == 403 && it.peekBody(4096).string().contains("insufficient", true) -> throw DriveException.Unauthorized()
                !it.isSuccessful -> throw DriveException.Server(it.code, it.body.string())
                else -> read(it)
            }
        }
    }

    companion object {
        const val PREFIX = "learn-backup-"
        const val ZIP = "application/zip"
    }
}

@Serializable private data class FileList(val files: List<DriveFile> = emptyList())

@Serializable private data class DriveFile(val id: String, val name: String = "", val createdTime: String? = null, val size: String? = null) {
    fun toSnapshot() = Snapshot(
        id, name,
        createdAt = createdTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
        sizeBytes = size?.toLongOrNull(),
    )
}
