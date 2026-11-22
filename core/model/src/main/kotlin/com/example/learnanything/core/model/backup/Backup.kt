package com.example.learnanything.core.model.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** manifest.json inside every backup zip. */
@Serializable
data class BackupManifest(
    /** Zip layout version. */
    val format: Int = FORMAT,
    /** Room schema version of the phone that wrote it. */
    val dbVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    /** Rows per table. */
    val tables: Map<String, Int>,
) {
    companion object {
        const val FORMAT = 1
        const val MANIFEST = "manifest.json"
        fun tableEntry(table: String) = "tables/$table.json"
    }
}

enum class RestoreMode { REPLACE, MERGE }

/**
 * Merge rules for one table. Every row has a UUID `id`, an `updatedAt` and a nullable
 * `deletedAt` tombstone, so two phones' rows can be combined without conflicts:
 * - a row only one side has is kept;
 * - when both have it, the newer `updatedAt` wins (a tie keeps this phone's row);
 * - deletes travel as tombstones, which win or lose like any other change.
 */
object BackupMerge {
    /** The incoming rows that should be written over this phone's rows. */
    fun rowsToWrite(local: List<JsonObject>, incoming: List<JsonObject>): List<JsonObject> {
        val localById = local.associateBy { it.id() }
        return incoming.filter { row ->
            val id = row.id() ?: return@filter false
            val mine = localById[id] ?: return@filter true
            row.updatedAt() > mine.updatedAt()
        }
    }

    /** Keeps the newest [keep] snapshots; returns the rest, oldest last, for deletion. */
    fun <T> toPrune(snapshots: List<T>, createdAt: (T) -> Long, keep: Int = KEEP_SNAPSHOTS): List<T> =
        snapshots.sortedByDescending(createdAt).drop(keep)

    const val KEEP_SNAPSHOTS = 5

    private fun JsonObject.id(): String? = (this["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.updatedAt(): Long = (this["updatedAt"] as? JsonPrimitive)?.longOrNull ?: Long.MIN_VALUE
}
