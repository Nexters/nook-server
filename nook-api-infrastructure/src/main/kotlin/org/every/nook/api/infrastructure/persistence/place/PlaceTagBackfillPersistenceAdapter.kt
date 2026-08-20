package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceTagBackfillPort
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceTagBackfillPersistenceAdapter(
    private val tagRepository: PostPlaceTagJpaRepository,
    private val placeRepository: PlaceJpaRepository,
) : PlaceTagBackfillPort {
    @Transactional(readOnly = true)
    override fun findAll(): List<PlaceTagsRequestedEvent> {
        val targets = tagRepository.findAllBackfillTargets()
        val placesById = placeRepository.findAllById(targets.map(PlaceTagBackfillProjection::placeId))
            .associateBy { requireNotNull(it.id) }
        return targets.groupBy(PlaceTagBackfillProjection::postId).mapNotNull { (postId, postTargets) ->
            val places = postTargets.mapNotNull { target -> target.toRequestedPlace(placesById[target.placeId]) }
            places.takeIf { it.isNotEmpty() }?.let { PlaceTagsRequestedEvent(postId, places) }
        }
    }

    private fun PlaceTagBackfillProjection.toRequestedPlace(place: PlaceEntity?): PlaceTagsRequestedEvent.Place? =
        place?.let {
            PlaceTagsRequestedEvent.Place(
                placeId = placeId,
                candidate = PlaceCandidate(
                    provider = it.provider,
                    externalPlaceId = it.externalPlaceId,
                    name = it.name,
                    address = it.address,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    category = it.category,
                    phoneNumber = it.phoneNumber,
                    providerUrl = null,
                    city = it.city,
                    googlePlaceId = it.googlePlaceId,
                ),
            )
        }
}
