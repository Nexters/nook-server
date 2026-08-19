package org.every.nook.api.infrastructure.persistence.place

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import mu.KotlinLogging
import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.domain.place.Place
import org.every.nook.api.domain.place.PlaceProviderReference
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.every.nook.api.infrastructure.persistence.BaseEntity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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
    var name: String,
    @Column(name = "address", nullable = false, length = Place.MAX_ADDRESS_LENGTH)
    var address: String,
    @Column(name = "city", nullable = true, length = Place.MAX_CITY_LENGTH)
    val city: String? = null,
    @Column(name = "latitude", nullable = false, precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
    val latitude: BigDecimal,
    @Column(name = "longitude", nullable = false, precision = COORDINATE_PRECISION, scale = COORDINATE_SCALE)
    val longitude: BigDecimal,
    @Column(name = "category", nullable = true, length = Place.MAX_CATEGORY_LENGTH)
    val category: String? = null,
    @Column(name = "phone_number", nullable = true, length = Place.MAX_PHONE_NUMBER_LENGTH)
    val phoneNumber: String? = null,
    @Column(name = "google_place_id", nullable = true, length = GOOGLE_PLACE_ID_MAX_LENGTH)
    var googlePlaceId: String? = null,
    @Column(name = "thumbnail_url", nullable = true, length = THUMBNAIL_URL_MAX_LENGTH)
    var thumbnailUrl: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(
        name = "thumbnail_parsing_status",
        nullable = false,
        length = THUMBNAIL_PARSING_STATUS_LENGTH,
        columnDefinition = "VARCHAR(20) COLLATE utf8mb4_bin",
    )
    var thumbnailParsingStatus: PlaceThumbnailParsingStatus = PlaceThumbnailParsingStatus.PENDING,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", nullable = true, columnDefinition = "JSON")
    var openingHours: PlaceOpeningHours? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photo_urls", nullable = false, columnDefinition = "JSON")
    var photoUrls: List<String> = emptyList(),
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "representative_tags", nullable = false, columnDefinition = "JSON")
    var representativeTags: List<PlaceTag> = emptyList(),
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    companion object {
        const val COORDINATE_PRECISION = 10
        const val COORDINATE_SCALE = 7
        const val THUMBNAIL_URL_MAX_LENGTH = 2048
        const val THUMBNAIL_PARSING_STATUS_LENGTH = 20
        const val GOOGLE_PLACE_ID_MAX_LENGTH = 255
        private const val MAX_REPRESENTATIVE_TAG_COUNT = 4
        private val logger = KotlinLogging.logger {}
    }

    fun updateThumbnailUrlIfAbsent(thumbnailUrl: String?) {
        when {
            thumbnailUrl == null -> logger.info {
                "Place thumbnail update skipped: reason=empty_thumbnail_url, placeId=$id, provider=$provider, " +
                    "externalPlaceId=$externalPlaceId"
            }

            this.thumbnailUrl != null -> logger.info {
                "Place thumbnail update skipped: reason=already_exists, placeId=$id, provider=$provider, " +
                    "externalPlaceId=$externalPlaceId"
            }

            else -> {
                this.thumbnailUrl = thumbnailUrl
                logger.info {
                    "Place thumbnail updated: placeId=$id, provider=$provider, externalPlaceId=$externalPlaceId, " +
                        "thumbnailUrl=$thumbnailUrl"
                }
            }
        }
    }

    fun updateSupplement(supplement: PlaceSupplement) {
        supplement.googlePlaceId?.let { googlePlaceId = it }
        supplement.openingHours?.let { openingHours = it }
        if (supplement.photoUrls.isNotEmpty()) {
            photoUrls = supplement.photoUrls
            if (supplement.replaceThumbnailUrl != null && thumbnailUrl == supplement.replaceThumbnailUrl) {
                thumbnailUrl = null
            }
            updateThumbnailUrlIfAbsent(supplement.photoUrls.first())
        }
    }

    fun updateThumbnailParsing(status: PlaceThumbnailParsingStatus, supplement: PlaceSupplement?) {
        supplement?.let(::updateSupplement)
        thumbnailParsingStatus = status
    }

    fun updateRepresentativeTags(tags: List<PlaceTag>) {
        representativeTags = tags.take(MAX_REPRESENTATIVE_TAG_COUNT)
    }

    fun updateBasicInformation(name: String, address: String) {
        require(name.isNotBlank() && name.length <= Place.MAX_NAME_LENGTH)
        require(address.isNotBlank() && address.length <= Place.MAX_ADDRESS_LENGTH)
        this.name = name
        this.address = address
    }
}

internal fun PlaceEntity.effectiveThumbnailParsingStatus(): PlaceThumbnailParsingStatus =
    effectiveThumbnailParsingStatus(thumbnailUrl, thumbnailParsingStatus)

internal fun effectiveThumbnailParsingStatus(
    thumbnailUrl: String?,
    status: PlaceThumbnailParsingStatus?,
): PlaceThumbnailParsingStatus = if (thumbnailUrl.isNullOrBlank()) {
    status ?: PlaceThumbnailParsingStatus.PENDING
} else {
    PlaceThumbnailParsingStatus.COMPLETED
}
