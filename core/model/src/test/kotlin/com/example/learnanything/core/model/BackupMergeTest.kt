package com.example.learnanything.core.model

import com.example.learnanything.core.model.backup.BackupMerge
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class BackupMergeTest {
    private fun row(id: String, updatedAt: Long, title: String = id, deletedAt: Long? = null) = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "title" to JsonPrimitive(title),
            "updatedAt" to JsonPrimitive(updatedAt),
            "deletedAt" to (deletedAt?.let { JsonPrimitive(it) } ?: JsonNull),
        ),
    )

    @Test fun newRowsAreAdded() {
        assertThat(BackupMerge.rowsToWrite(listOf(row("a", 1)), listOf(row("b", 1)))).containsExactly(row("b", 1))
    }

    @Test fun newerIncomingWinsOlderLoses() {
        val local = listOf(row("a", 5, "mine"), row("b", 5, "mine"))
        val incoming = listOf(row("a", 9, "theirs"), row("b", 2, "theirs"))
        assertThat(BackupMerge.rowsToWrite(local, incoming)).containsExactly(row("a", 9, "theirs"))
    }

    @Test fun tieKeepsThisPhonesRow() {
        assertThat(BackupMerge.rowsToWrite(listOf(row("a", 5, "mine")), listOf(row("a", 5, "theirs")))).isEmpty()
    }

    @Test fun newerTombstoneDeletesAndOlderTombstoneDoesNot() {
        val local = listOf(row("a", 5), row("b", 9))
        val incoming = listOf(row("a", 7, deletedAt = 7), row("b", 3, deletedAt = 3))
        assertThat(BackupMerge.rowsToWrite(local, incoming)).containsExactly(row("a", 7, deletedAt = 7))
    }

    @Test fun localTombstoneSurvivesAnOlderLiveRow() {
        assertThat(BackupMerge.rowsToWrite(listOf(row("a", 8, deletedAt = 8)), listOf(row("a", 4)))).isEmpty()
    }

    @Test fun rowsWithoutAnIdAreIgnored() {
        assertThat(BackupMerge.rowsToWrite(emptyList(), listOf(JsonObject(mapOf("updatedAt" to JsonPrimitive(1)))))).isEmpty()
    }

    @Test fun pruneKeepsTheNewestFive() {
        val snaps = (1L..7L).toList()
        assertThat(BackupMerge.toPrune(snaps, { it })).containsExactly(2L, 1L).inOrder()
        assertThat(BackupMerge.toPrune((1L..3L).toList(), { it })).isEmpty()
    }
}
