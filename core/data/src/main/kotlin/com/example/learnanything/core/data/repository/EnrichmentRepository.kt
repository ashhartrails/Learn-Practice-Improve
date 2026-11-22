package com.example.learnanything.core.data.repository

import com.example.learnanything.core.data.TimeSource
import com.example.learnanything.core.data.db.AppDatabase
import com.example.learnanything.core.data.db.LearnerProfileEntity
import com.example.learnanything.core.data.db.RescueEventEntity
import com.example.learnanything.core.data.db.ResourceEntity
import com.example.learnanything.core.model.ModelJson
import com.example.learnanything.core.model.rescue.RescueLadder
import com.example.learnanything.core.model.rescue.RescueRung
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

data class LearnerProfile(val background: String = "", val knownSkills: String = "")

enum class ResourceType { YOUTUBE, PHET }

data class StoredResource(val id: String, val type: ResourceType, val sourceRef: String, val title: String, val rating: Int?)

/** Videos, simulations, the rescue ladder history and the learner profile. */
@Singleton
class EnrichmentRepository @Inject constructor(
    db: AppDatabase,
    private val time: TimeSource,
) {
    private val dao = db.enrichmentDao()
    private val rungList = ListSerializer(String.serializer())

    fun observeProfile(): Flow<LearnerProfile> = dao.observeProfile(ME).map { it.toDomain() }

    suspend fun profile(): LearnerProfile = dao.profile(ME).toDomain()

    suspend fun saveProfile(background: String, knownSkills: String) {
        val existing = dao.profile(ME)
        dao.upsertProfile(
            LearnerProfileEntity(
                id = ME,
                background = background.trim().take(MAX_PROFILE),
                knownSkills = knownSkills.trim().take(MAX_PROFILE),
                ladderOrderJson = existing?.ladderOrderJson,
                updatedAt = time.now(),
            ),
        )
    }

    /** The ladder for this learner: the rung that has helped most often comes first. */
    suspend fun ladderOrder(): List<RescueRung> {
        val counts = dao.helpedCounts().mapNotNull { row ->
            RescueRung.entries.firstOrNull { it.name == row.rung }?.let { it to row.helpedCount }
        }.toMap()
        return RescueLadder.order(counts)
    }

    suspend fun recordRescue(topicId: String, rung: RescueRung, helped: Boolean) {
        val now = time.now()
        dao.upsertRescueEvent(RescueEventEntity(UUID.randomUUID().toString(), topicId, rung.name, helped, now, now))
        if (helped) {
            val order = ladderOrder()
            val existing = dao.profile(ME)
            dao.upsertProfile(
                (existing ?: LearnerProfileEntity(ME, "", "", updatedAt = now)).copy(
                    ladderOrderJson = ModelJson.encodeToString(rungList, order.map { it.name }),
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun resource(topicId: String, type: ResourceType): StoredResource? =
        dao.resource(topicId, type.name)?.let { StoredResource(it.id, type, it.sourceRef, it.title, it.rating) }

    suspend fun saveResource(topicId: String, type: ResourceType, sourceRef: String, title: String): StoredResource {
        val now = time.now()
        dao.tombstoneResources(topicId, type.name, now)
        val entity = ResourceEntity(UUID.randomUUID().toString(), topicId, type.name, sourceRef, title, updatedAt = now)
        dao.upsertResource(entity)
        return StoredResource(entity.id, type, sourceRef, title, null)
    }

    /** Thumbs down also forgets the video, so the next request finds another one. */
    suspend fun rate(topicId: String, resource: StoredResource, rating: Int) {
        val now = time.now()
        dao.upsertResource(
            ResourceEntity(resource.id, topicId, resource.type.name, resource.sourceRef, resource.title, rating = rating, updatedAt = now,
                deletedAt = if (rating < 0) now else null),
        )
    }

    private fun LearnerProfileEntity?.toDomain() = LearnerProfile(this?.background.orEmpty(), this?.knownSkills.orEmpty())

    private companion object {
        const val ME = "me"
        const val MAX_PROFILE = 500
    }
}
