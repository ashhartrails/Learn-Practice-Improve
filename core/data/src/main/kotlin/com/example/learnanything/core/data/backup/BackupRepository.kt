package com.example.learnanything.core.data.backup

import android.content.Context
import android.net.Uri
import com.example.learnanything.core.data.BuildConfig
import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.model.backup.BackupManifest
import com.example.learnanything.core.model.backup.BackupMerge
import com.example.learnanything.core.model.backup.RestoreMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface BackupOutcome {
    data class Done(val message: String) : BackupOutcome
    /** The learner must turn backup on (sign in and grant Drive access) in Settings. */
    data object NeedsAccess : BackupOutcome
    data class Failed(val message: String, val retryable: Boolean) : BackupOutcome
}

/** Backups to the hidden Drive app folder, restores (Replace or Merge), and zip export/import. */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BackupStore,
    private val drive: DriveClient,
    private val auth: DriveAuth,
    private val prefs: BackupPrefs,
    private val time: TimeSource,
) {
    val status = prefs.status

    /** Builds the zip from the current database. Keys, model files and the AI cache are never in it. */
    suspend fun snapshotBytes(): ByteArray {
        val tables = store.dump()
        val manifest = BackupManifest(
            dbVersion = DB_VERSION,
            createdAt = time.now(),
            appVersion = APP_VERSION,
            tables = tables.mapValues { it.value.size },
        )
        return withContext(Dispatchers.IO) { BackupZip.toBytes(BackupContents(manifest, tables)) }
    }

    suspend fun backupNow(): BackupOutcome {
        if (!prefs.isEnabled()) return BackupOutcome.NeedsAccess
        val token = auth.accessToken() ?: return BackupOutcome.NeedsAccess.also { prefs.recordError("Drive access needs to be granted again.") }
        return try {
            val bytes = snapshotBytes()
            drive.upload(token, fileName(time.now()), bytes)
            BackupMerge.toPrune(drive.list(token), { it.createdAt }).forEach { drive.delete(token, it.id) }
            prefs.recordSuccess(time.now())
            BackupOutcome.Done("Backed up (${bytes.size / 1024 + 1} KB).")
        } catch (e: DriveException) {
            prefs.recordError(e.message.orEmpty())
            if (e is DriveException.Unauthorized) BackupOutcome.NeedsAccess else BackupOutcome.Failed(e.message.orEmpty(), retryable = true)
        }
    }

    suspend fun snapshots(): Result<List<Snapshot>> {
        val token = auth.accessToken() ?: return Result.failure(DriveException.Unauthorized())
        return try {
            Result.success(drive.list(token))
        } catch (e: DriveException) {
            Result.failure(e)
        }
    }

    suspend fun restore(snapshot: Snapshot, mode: RestoreMode): BackupOutcome {
        val token = auth.accessToken() ?: return BackupOutcome.NeedsAccess
        return try {
            val bytes = drive.download(token, snapshot.id)
            apply(bytes.inputStream().use { withContext(Dispatchers.IO) { BackupZip.read(it) } }, mode)
        } catch (e: IOException) {
            BackupOutcome.Failed(e.message ?: "Restore failed.", retryable = e is DriveException.Network)
        }
    }

    /** Export through the Storage Access Framework: the learner picks where the zip goes. */
    suspend fun exportTo(uri: Uri): BackupOutcome = try {
        val bytes = snapshotBytes()
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Couldn't open that file.")
        }
        BackupOutcome.Done("Exported (${bytes.size / 1024 + 1} KB).")
    } catch (e: IOException) {
        BackupOutcome.Failed(e.message ?: "Export failed.", retryable = false)
    }

    suspend fun importFrom(uri: Uri, mode: RestoreMode): BackupOutcome = try {
        val contents = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { BackupZip.read(it) } ?: throw IOException("Couldn't open that file.")
        }
        apply(contents, mode)
    } catch (e: IOException) {
        BackupOutcome.Failed(e.message ?: "Import failed.", retryable = false)
    }

    internal suspend fun apply(contents: BackupContents, mode: RestoreMode): BackupOutcome {
        if (contents.manifest.dbVersion > DB_VERSION) {
            return BackupOutcome.Failed("This backup was made by a newer version of the app. Update the app on this phone first.", retryable = false)
        }
        val written = store.write(contents.tables, mode)
        val total = contents.tables.values.sumOf { it.size }
        return BackupOutcome.Done(
            when (mode) {
                RestoreMode.REPLACE -> "Restored $total records."
                RestoreMode.MERGE -> "Merged: $written of $total records were newer and were applied."
            },
        )
    }

    companion object {
        /** Keep in step with AppDatabase.version. */
        const val DB_VERSION = 4
        val APP_VERSION: String = BuildConfig.LIBRARY_PACKAGE_NAME
        private val NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

        fun fileName(now: Long) = "${DriveClient.PREFIX}${NAME_TIME.format(Instant.ofEpochMilli(now))}.zip"

        /** Set from local.properties `google.webClientId`; empty means sign in with authorization only. */
        val WEB_CLIENT_ID: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
    }
}

/** Schedules background backups. Implemented with WorkManager in the app module. */
interface BackupScheduler {
    /** A chapter was finished: back up soon (debounced). */
    fun chapterFinished()
    fun setDaily(enabled: Boolean)
}
