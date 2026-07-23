package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceProviderReference
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.math.BigDecimal

@Entity
@Table(
    name = "places",
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_provider_external_place_id",
            columnNames = ["provider", "external_place_id"],
        ),
    ],
)
class PlaceEntity(
    @Column(
        name = "provider",
        nullable = false,
        length = PlaceProviderReference.MAX_PROVIDER_LENGTH,
        columnDefinition = "VARCHAR(50) COLLATE utf8mb4_bin",
    )
    val provider: String,
    @Column(
        name = "external_place_id",
        nullable = false,
        length = PlaceProviderReference.MAX_EXTERNAL_PLACE_ID_LENGTH,
        columnDefinition = "VARCHAR(255) COLLATE utf8mb4_bin",
    )
    val externalPlaceId: String,
    @Column(name = "name", nullable = false, length = Place.MAX_NAME_LENGTH)
    val name: String,
    @Column(name = "address", nullable = false, length = Place.MAX_ADDRESS_LENGTH)
    val address: String,
    @Column(name = "latitude", nullable = false, precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
    val latitude: BigDecimal,
    @Column(name = "longitude", nullable = false, precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
    val longitude: BigDecimal,
    @Column(name = "category", nullable = true, length = Place.MAX_CATEGORY_LENGTH)
    val category: String? = null,
    @Column(name = "phone_number", nullable = true, length = Place.MAX_PHONE_NUMBER_LENGTH)
    val phoneNumber: String? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val COORDINATE_PRECISION = 10
        const val COORDINATE_SCALE = 7
    }
}
