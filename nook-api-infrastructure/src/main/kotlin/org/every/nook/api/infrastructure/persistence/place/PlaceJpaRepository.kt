package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.JpaRepository

interface PlaceJpaRepository : JpaRepository<PlaceEntity, Long> {
    fun findByProviderAndExternalPlaceId(provider: String, externalPlaceId: String): PlaceEntity?
}
