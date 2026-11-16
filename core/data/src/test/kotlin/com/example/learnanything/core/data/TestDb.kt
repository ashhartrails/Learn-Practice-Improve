package com.example.learnanything.core.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.example.learnanything.core.ai.AiChunk
import com.example.learnanything.core.ai.AiProvider
import com.example.learnanything.core.ai.AiRequest
import com.example.learnanything.core.ai.ModelInfo
import com.example.learnanything.core.ai.ProviderKind
import com.example.learnanything.core.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .setDriver(AndroidSQLiteDriver())
        .build()

class TestClock(var now: Long = 1_000_000L) : TimeSource {
    override fun now() = now
}

const val DAY = 24L * 60 * 60 * 1000

/** Replies by call type; counts calls. */
class FakeAi(private val replies: Map<com.example.learnanything.core.model.ai.CallType, () -> String>) : AiProvider {
    override val id = "fake"
    override val displayName = "Fake"
    override val kind = ProviderKind.CLOUD
    val calls = mutableListOf<AiRequest>()
    override suspend fun isConfigured() = true
    override suspend fun listModels() = listOf(ModelInfo("m", "m"))
    override fun generate(request: AiRequest): Flow<AiChunk> = flow {
        calls += request
        emit(AiChunk.Text(replies.getValue(request.callType)()))
        emit(AiChunk.Done("m", "STOP"))
    }
}

object Fixtures {
    const val Q1 = """{"kind":"single_choice","prompt":"Which join keeps every left row?","options":["INNER","LEFT","CROSS"],"correct":[1],"accepted":[],"explanation":"LEFT keeps them."}"""
    const val Q2 = """{"kind":"numeric","prompt":"Rows in a 3 x 4 cross join?","options":[],"correct":[],"number":12,"tolerance":0,"accepted":[],"explanation":"3 times 4."}"""
    const val Q3 = """{"kind":"short_text","prompt":"Keyword that filters rows?","options":[],"correct":[],"accepted":["WHERE"],"explanation":"WHERE filters."}"""

    const val LESSON = """
        {"whyItMatters":"Joins answer questions across tables.",
         "cards":[{"heading":"Idea","body":"A join combines rows from two tables."},{"heading":"Keys","body":"Rows match on a **key**."}],
         "workedExample":{"problem":"Orders with customer names.","steps":["Pick tables","Match on customer_id"],"answer":"SELECT ..."},
         "tryIt":$Q1, "checks":[$Q2,$Q3]}
    """

    fun quiz(topicCount: Int): String =
        (0 until 8).joinToString(",", "{\"questions\":[", "]}") { i ->
            val q = listOf(Q1, Q2, Q3)[i % 3].replace("\"prompt\":\"", "\"prompt\":\"($i) ")
            """{"topicIndex":${i % topicCount},"question":$q}"""
        }
}
