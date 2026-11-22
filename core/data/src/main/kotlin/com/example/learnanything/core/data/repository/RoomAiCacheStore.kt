package com.example.learnanything.core.data.repository

import com.example.learnanything.core.ai.AiCacheStore
import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.db.AiCacheDao
import com.example.learnanything.core.data.db.AiCacheEntity
import com.example.learnanything.core.model.ai.CallType
import javax.inject.Inject

class RoomAiCacheStore @Inject constructor(
    private val dao: AiCacheDao,
    private val time: TimeSource,
) : AiCacheStore {
    override suspend fun get(promptHash: String): String? = dao.get(promptHash)

    override suspend fun put(promptHash: String, callType: CallType, response: String) {
        val now = time.now()
        dao.put(AiCacheEntity(promptHash, callType.name, response, createdAt = now, updatedAt = now))
    }
}
