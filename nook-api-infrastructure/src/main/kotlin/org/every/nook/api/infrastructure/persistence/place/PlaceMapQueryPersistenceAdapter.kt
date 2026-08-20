package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.MapPlaceView
import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.every.nook.api.application.place.PlaceTagCatalogSnapshot
import org.every.nook.api.application.place.PlaceThumbnailParsingStatusView
import org.every.nook.api.application.place.RecentPlaceCursor
import org.every.nook.api.application.place.RecentPlaceView
import org.every.nook.api.application.place.port.PlaceMapQueryPort
import org.every.nook.api.application.place.snapshot
import org.every.nook.api.domain.place.GeoBounds
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper

@Component
class PlaceMapQueryPersistenceAdapter(
    private val bookmarkRepository: UserPlaceBookmarkJpaRepository,
    private val tagCatalogPort: PlaceTagCatalogQueryPort = PlaceTagCatalogQueryPort { PlaceTag.defaultDefinitions },
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : PlaceMapQueryPort {
    @Transactional(readOnly = true)
    override fun findInBounds(userId: Long, bounds: GeoBounds): List<MapPlaceView> {
        val tagCatalog = tagCatalogPort.snapshot()
        return bookmarkRepository.findMapPlaces(
            userId = userId,
            northLatitude = bounds.northLatitude,
            westLongitude = bounds.westLongitude,
            southLatitude = bounds.southLatitude,
            eastLongitude = bounds.eastLongitude,
        ).map { row ->
            MapPlaceView(
                id = row.id,
                name = row.name,
                city = row.city,
                category = row.category,
                latitude = row.latitude,
                longitude = row.longitude,
                color = row.color,
                thumbnailUrl = row.thumbnailUrl,
                thumbnailParsingStatus = PlaceThumbnailParsingStatusView.from(
                    effectiveThumbnailParsingStatus(
                        row.thumbnailUrl,
                        row.thumbnailParsingStatus?.let(PlaceThumbnailParsingStatus::valueOf),
                    ),
                ),
                tags = row.representativeTags.toDisplayTags(tagCatalog),
            )
        }
    }

    @Transactional(readOnly = true)
    override fun findRecent(userId: Long, cursor: RecentPlaceCursor?, limit: Int): List<RecentPlaceView> {
        val tagCatalog = tagCatalogPort.snapshot()
        return bookmarkRepository.findRecentPlaces(
            userId = userId,
            cursorBookmarkedAt = cursor?.bookmarkedAt,
            cursorBookmarkId = cursor?.bookmarkId,
            limit = limit,
        ).map { row ->
            RecentPlaceView(
                bookmarkId = row.bookmarkId,
                bookmarkedAt = row.bookmarkedAt,
                id = row.placeId,
                name = row.name,
                city = row.city,
                address = row.address,
                category = row.category,
                latitude = row.latitude,
                longitude = row.longitude,
                thumbnailUrl = row.thumbnailUrl,
                thumbnailParsingStatus = PlaceThumbnailParsingStatusView.from(
                    effectiveThumbnailParsingStatus(
                        row.thumbnailUrl,
                        row.thumbnailParsingStatus?.let(PlaceThumbnailParsingStatus::valueOf),
                    ),
                ),
                tags = row.representativeTags.toDisplayTags(tagCatalog),
            )
        }
    }

    private fun String?.toDisplayTags(tagCatalog: PlaceTagCatalogSnapshot): List<String> = if (this.isNullOrBlank()) {
        emptyList()
    } else {
        tagCatalog.displayNames(objectMapper.readValue(this, Array<String>::class.java).asList())
    }
}
