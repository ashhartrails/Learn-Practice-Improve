package com.example.learnanything.core.data.backup

import com.example.learnanything.core.model.ModelJson
import com.example.learnanything.core.model.backup.BackupManifest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

data class BackupContents(val manifest: BackupManifest, val tables: Map<String, List<JsonObject>>)

class BackupFormatException(message: String) : IOException(message)

/** A zip of JSON: manifest.json plus tables/<name>.json for each table. */
object BackupZip {
    private val rows = ListSerializer(JsonObject.serializer())
    private const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    fun write(contents: BackupContents, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST))
            zip.write(ModelJson.encodeToString(BackupManifest.serializer(), contents.manifest).toByteArray())
            zip.closeEntry()
            contents.tables.forEach { (table, list) ->
                zip.putNextEntry(ZipEntry(BackupManifest.tableEntry(table)))
                zip.write(ModelJson.encodeToString(rows, list).toByteArray())
                zip.closeEntry()
            }
        }
    }

    fun toBytes(contents: BackupContents): ByteArray = ByteArrayOutputStream().also { write(contents, it) }.toByteArray()

    /** Reads only the expected entries; anything else in the zip is ignored. */
    fun read(input: InputStream): BackupContents {
        var manifest: BackupManifest? = null
        val tables = mutableMapOf<String, List<JsonObject>>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val isTable = name.startsWith("tables/") && name.endsWith(".json") && BackupStore.TABLES.contains(name.removePrefix("tables/").removeSuffix(".json"))
                if (name != BackupManifest.MANIFEST && !isTable) continue
                val text = readLimited(zip).decodeToString()
                try {
                    if (name == BackupManifest.MANIFEST) manifest = ModelJson.decodeFromString(BackupManifest.serializer(), text)
                    else tables[name.removePrefix("tables/").removeSuffix(".json")] = ModelJson.decodeFromString(rows, text)
                } catch (e: IllegalArgumentException) {
                    throw BackupFormatException("This backup file is damaged ($name).")
                }
            }
        }
        val m = manifest ?: throw BackupFormatException("This isn't a Learn Anything backup (no manifest).")
        if (m.format > BackupManifest.FORMAT) throw BackupFormatException("This backup was made by a newer version of the app. Update the app first.")
        return BackupContents(m, tables)
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw BackupFormatException("This backup is too large to open.")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
