package com.example.learnanything.core.data.backup

import androidx.room3.PooledConnection
import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteStatement
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.model.backup.BackupMerge
import com.example.learnanything.core.model.backup.RestoreMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads and writes the backed-up tables as JSON rows, column by column, so the format follows
 * the Room schema without hand-written mappers. The AI cache is never included.
 */
@Singleton
class BackupStore @Inject constructor(private val db: AppDatabase) {

    suspend fun dump(): Map<String, List<JsonObject>> = db.useReaderConnection { c ->
        TABLES.associateWith { table -> c.usePrepared("SELECT * FROM `$table`") { readRows(it) } }
    }

    /**
     * Writes a backup into the database in one transaction. REPLACE clears the backed-up tables
     * first; MERGE writes only rows that are newer than this phone's (see [BackupMerge]).
     * Returns the number of rows written.
     */
    suspend fun write(tables: Map<String, List<JsonObject>>, mode: RestoreMode): Int {
        val local = if (mode == RestoreMode.MERGE) dump() else emptyMap()
        return db.useWriterConnection { t ->
            t.immediateTransaction {
                if (mode == RestoreMode.REPLACE) TABLES.reversed().forEach { executeSQL("DELETE FROM `$it`") }
                var written = 0
                for (table in TABLES) {
                    val incoming = tables[table].orEmpty()
                    val rows = if (mode == RestoreMode.MERGE) BackupMerge.rowsToWrite(local[table].orEmpty(), incoming) else incoming
                    if (rows.isEmpty()) continue
                    val columns = columnsOf(table)
                    rows.forEach { upsert(table, columns, it) }
                    written += rows.size
                }
                written
            }
        }
    }

    private suspend fun PooledConnection.columnsOf(table: String): List<String> =
        usePrepared("PRAGMA table_info(`$table`)") { st ->
            buildList { while (st.step()) add(st.getText(1)) }
        }

    /** Insert or update by id. No REPLACE: that would delete the row first and cascade to children. */
    private suspend fun PooledConnection.upsert(table: String, columns: List<String>, row: JsonObject) {
        val cols = columns.filter { it in row }
        if ("id" !in cols) return
        val names = cols.joinToString(",") { "`$it`" }
        val marks = cols.joinToString(",") { "?" }
        val updates = cols.filter { it != "id" }.joinToString(",") { "`$it`=excluded.`$it`" }
        val sql = "INSERT INTO `$table` ($names) VALUES ($marks) ON CONFLICT(`id`) DO " +
            if (updates.isEmpty()) "NOTHING" else "UPDATE SET $updates"
        usePrepared(sql) { st ->
            cols.forEachIndexed { i, c -> bind(st, i + 1, row[c] ?: JsonNull) }
            st.step()
        }
    }

    private fun readRows(st: SQLiteStatement): List<JsonObject> = buildList {
        while (st.step()) {
            add(
                JsonObject(
                    (0 until st.getColumnCount()).associate { i ->
                        st.getColumnName(i) to when (st.getColumnType(i)) {
                            SQLITE_DATA_NULL -> JsonNull
                            SQLITE_DATA_INTEGER -> JsonPrimitive(st.getLong(i))
                            SQLITE_DATA_FLOAT -> JsonPrimitive(st.getDouble(i))
                            else -> JsonPrimitive(st.getText(i))
                        }
                    },
                ),
            )
        }
    }

    private fun bind(st: SQLiteStatement, index: Int, value: JsonElement) {
        val p = value as? JsonPrimitive
        when {
            p == null || p is JsonNull -> st.bindNull(index)
            p.isString -> st.bindText(index, p.content)
            p.longOrNull != null -> st.bindLong(index, p.longOrNull!!)
            p.doubleOrNull != null -> st.bindDouble(index, p.doubleOrNull!!)
            p.booleanOrNull != null -> st.bindLong(index, if (p.booleanOrNull!!) 1 else 0)
            else -> st.bindText(index, p.content)
        }
    }

    companion object {
        /** Parents before children (foreign keys). The AI cache is deliberately absent. */
        val TABLES = listOf(
            "subject", "chapter", "topic", "item", "attempt", "concept",
            "resource", "rescue_event", "learner_profile",
        )
    }
}
