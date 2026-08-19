package org.every.nook.api.infrastructure.persistence.place

import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceCandidate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class PlaceIdentityResolver(
    private val placeRepository: PlaceJpaRepository,
    private val referenceRepository: PlaceProviderReferenceJpaRepository,
    private val matcher: PlaceIdentityMatcher,
) {
    fun resolve(candidate: PlaceCandidate): PlaceEntity {
        find(candidate.provider, candidate.externalPlaceId)?.let { return it }

        val matchedPlaces = findCanonicalPlacesNear(candidate).filter { matcher.matches(it, candidate) }
        val resolved = if (matchedPlaces.size == 1) {
            matchedPlaces.single().also { place ->
                logger.info {
                    "Cross-provider place identity matched: canonicalPlaceId=${place.id}, " +
                        "canonicalProvider=${place.provider}, candidateProvider=${candidate.provider}, " +
                        "candidateExternalPlaceId=${candidate.externalPlaceId}, name=${candidate.name}"
                }
            }
        } else {
            if (matchedPlaces.size > 1) {
                logger.warn {
                    "Cross-provider place identity was ambiguous: candidateProvider=${candidate.provider}, " +
                        "candidateExternalPlaceId=${candidate.externalPlaceId}, " +
                        "matchedPlaceIds=${matchedPlaces.mapNotNull(PlaceEntity::id)}"
                }
            }
            create(candidate)
        }
        registerReference(resolved, candidate.provider, candidate.externalPlaceId)
        return find(candidate.provider, candidate.externalPlaceId) ?: resolved
    }

    fun find(provider: String, externalPlaceId: String): PlaceEntity? =
        referenceRepository.findByProviderAndExternalPlaceId(provider, externalPlaceId)
            ?.let { reference -> placeRepository.findByIdOrNull(reference.placeId) }
            ?: placeRepository.findByProviderAndExternalPlaceId(provider, externalPlaceId)?.also { place ->
                registerReference(place, provider, externalPlaceId)
            }

    private fun findCanonicalPlacesNear(candidate: PlaceCandidate): List<PlaceEntity> =
        placeRepository.findAllByLatitudeBetweenAndLongitudeBetween(
            candidate.latitude.subtract(LATITUDE_SEARCH_DELTA),
            candidate.latitude.add(LATITUDE_SEARCH_DELTA),
            candidate.longitude.subtract(LONGITUDE_SEARCH_DELTA),
            candidate.longitude.add(LONGITUDE_SEARCH_DELTA),
        ).filter(::isCanonical)

    private fun isCanonical(place: PlaceEntity): Boolean {
        val reference = referenceRepository.findByProviderAndExternalPlaceId(place.provider, place.externalPlaceId)
        return reference == null || reference.placeId == place.id
    }

    private fun create(candidate: PlaceCandidate): PlaceEntity {
        placeRepository.insertIgnore(
            provider = candidate.provider,
            externalPlaceId = candidate.externalPlaceId,
            name = candidate.name,
            address = candidate.address,
            city = candidate.city,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            category = candidate.category,
            phoneNumber = candidate.phoneNumber,
        )
        return requireNotNull(
            placeRepository.findByProviderAndExternalPlaceId(candidate.provider, candidate.externalPlaceId),
        )
    }

    private fun registerReference(place: PlaceEntity, provider: String, externalPlaceId: String) {
        referenceRepository.insertIgnore(
            placeId = requireNotNull(place.id),
            provider = provider,
            externalPlaceId = externalPlaceId,
        )
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        val LATITUDE_SEARCH_DELTA = BigDecimal("0.0005")
        val LONGITUDE_SEARCH_DELTA = BigDecimal("0.0007")
    }
}
