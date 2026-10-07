package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.PlaceReprocessingSource
import org.every.nook.api.application.admin.PlaceReprocessingSourcePort
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PostPlaceTagJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceReprocessingSourceAdapter(
    private val places: PlaceJpaRepository,
    private val tags: PostPlaceTagJpaRepository,
) : PlaceReprocessingSourcePort {
    @Transactional(readOnly = true)
    override fun find(placeId: Long): PlaceReprocessingSource? {
        val place = places.findById(placeId).orElse(null) ?: return null
        val targets = tags.findReprocessingContexts(placeId)
        val related = places.findAllById(targets.map { it.placeId }).associateBy { requireNotNull(it.id) }
        val events = targets.groupBy { it.postId }.mapNotNull { (postId, mappings) ->
            val candidates = mappings.mapNotNull { mapping ->
                related[mapping.placeId]?.let { PlaceTagsRequestedEvent.Place(mapping.placeId, it.toCandidate()) }
            }
            candidates.takeIf { it.isNotEmpty() }?.let { PlaceTagsRequestedEvent(postId, it) }
        }
        return PlaceReprocessingSource(
            place.toCandidate(),
            place.thumbnailUrl,
            place.photoUrls,
            place.representativeTags,
            events,
        )
    }
}

internal fun PlaceEntity.toCandidate() = PlaceCandidate(
    provider = provider,
    externalPlaceId = externalPlaceId,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    category = category,
    phoneNumber = phoneNumber,
    providerUrl = null,
    city = city,
    googlePlaceId = googlePlaceId,
)
