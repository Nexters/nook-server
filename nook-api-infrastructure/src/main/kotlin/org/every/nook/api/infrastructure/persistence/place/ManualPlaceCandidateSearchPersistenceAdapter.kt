package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.ManualPlaceCandidateSearchPort
import org.every.nook.api.application.place.PlaceCandidate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ManualPlaceCandidateSearchPersistenceAdapter(private val placeRepository: PlaceJpaRepository) :
    ManualPlaceCandidateSearchPort {
    @Transactional(readOnly = true)
    override fun findByName(name: String): List<PlaceCandidate> =
        placeRepository.findAllByProviderAndNameIgnoreCaseOrderByIdAsc(
            ManualPlaceCandidateSearchPort.PROVIDER,
            name,
        ).map { place ->
            PlaceCandidate(
                provider = place.provider,
                externalPlaceId = place.externalPlaceId,
                name = place.name,
                address = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
                category = place.category,
                phoneNumber = place.phoneNumber,
                providerUrl = null,
                city = place.city,
                googlePlaceId = place.googlePlaceId,
            )
        }
}
