package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal

interface PlaceJpaRepository : JpaRepository<PlaceEntity, Long> {
    fun findByProviderAndExternalPlaceId(provider: String, externalPlaceId: String): PlaceEntity?

    fun findAllByLatitudeBetweenAndLongitudeBetween(
        minimumLatitude: BigDecimal,
        maximumLatitude: BigDecimal,
        minimumLongitude: BigDecimal,
        maximumLongitude: BigDecimal,
    ): List<PlaceEntity>

    fun findAllByLatitudeAndLongitude(latitude: BigDecimal, longitude: BigDecimal): List<PlaceEntity>

    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO places (
                provider,
                external_place_id,
                name,
                address,
                city,
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
                :city,
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
        @Param("city") city: String?,
        @Param("latitude") latitude: BigDecimal,
        @Param("longitude") longitude: BigDecimal,
        @Param("category") category: String?,
        @Param("phoneNumber") phoneNumber: String?,
    ): Int

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT place FROM PlaceEntity place WHERE place.id = :placeId")
    fun findByIdForUpdate(@Param("placeId") placeId: Long): PlaceEntity?
}
