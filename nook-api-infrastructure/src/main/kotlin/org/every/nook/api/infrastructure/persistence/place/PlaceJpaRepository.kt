package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface PlaceJpaRepository : JpaRepository<PlaceEntity, Long> {
    fun findByProviderAndExternalPlaceId(provider: String, externalPlaceId: String): PlaceEntity?

    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO places (
                provider,
                external_place_id,
                name,
                address,
                latitude,
                longitude,
                category,
                phone_number,
                created_at,
                updated_at
            ) VALUES (
                :provider,
                :externalPlaceId,
                :name,
                :address,
                :latitude,
                :longitude,
                :category,
                :phoneNumber,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
        """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("provider") provider: String,
        @Param("externalPlaceId") externalPlaceId: String,
        @Param("name") name: String,
        @Param("address") address: String,
        @Param("latitude") latitude: BigDecimal,
        @Param("longitude") longitude: BigDecimal,
        @Param("category") category: String?,
        @Param("phoneNumber") phoneNumber: String?,
    ): Int
}
