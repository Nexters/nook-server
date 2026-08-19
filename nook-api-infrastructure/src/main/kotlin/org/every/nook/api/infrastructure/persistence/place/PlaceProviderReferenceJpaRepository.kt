package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PlaceProviderReferenceJpaRepository : JpaRepository<PlaceProviderReferenceEntity, Long> {
    fun findByProviderAndExternalPlaceId(provider: String, externalPlaceId: String): PlaceProviderReferenceEntity?

    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO place_provider_references (
                place_id,
                provider,
                external_place_id,
                created_at,
                updated_at
            ) VALUES (
                :placeId,
                :provider,
                :externalPlaceId,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
        """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("placeId") placeId: Long,
        @Param("provider") provider: String,
        @Param("externalPlaceId") externalPlaceId: String,
    ): Int
}
