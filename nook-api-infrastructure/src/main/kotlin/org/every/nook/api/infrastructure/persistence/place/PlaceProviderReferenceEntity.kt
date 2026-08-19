package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.domain.place.PlaceProviderReference
import org.every.nook.api.infrastructure.persistence.BaseEntity

@Entity
@Table(
    name = "place_provider_references",
    indexes = [Index(name = "idx_place_id", columnList = "place_id")],
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_provider_external_place_id",
            columnNames = ["provider", "external_place_id"],
        ),
    ],
)
class PlaceProviderReferenceEntity(
    @Column(name = "place_id", nullable = false)
    val placeId: Long,
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
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}
