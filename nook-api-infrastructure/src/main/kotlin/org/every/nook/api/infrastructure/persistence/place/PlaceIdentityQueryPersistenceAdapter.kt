package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.port.PlaceIdentityQueryPort
import org.every.nook.api.domain.place.PlaceProviderReference
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PlaceIdentityQueryPersistenceAdapter(private val placeRepository: PlaceJpaRepository) : PlaceIdentityQueryPort {
    @Transactional(readOnly = true)
    override fun findIds(references: Set<PlaceProviderReference>): Map<PlaceProviderReference, Long> {
        if (references.isEmpty()) return emptyMap()
        val providers = references.mapTo(mutableSetOf(), PlaceProviderReference::provider)
        val externalPlaceIds = references.mapTo(mutableSetOf(), PlaceProviderReference::externalPlaceId)
        return placeRepository.findAllByProviderInAndExternalPlaceIdIn(providers, externalPlaceIds)
            .mapNotNull { place ->
                val reference = PlaceProviderReference(place.provider, place.externalPlaceId)
                if (reference in references) requireNotNull(place.id).let { reference to it } else null
            }
            .toMap()
    }
}
