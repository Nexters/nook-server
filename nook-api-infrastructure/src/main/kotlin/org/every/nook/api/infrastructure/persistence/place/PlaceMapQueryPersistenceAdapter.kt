package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.MapPlaceView
import org.every.nook.api.application.place.PlaceAccessType
import org.every.nook.api.application.place.PlaceCategoryGroup
import org.every.nook.api.application.place.PlaceOrdering
import org.every.nook.api.application.place.PlaceSort
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
    private val recentCountRepository: RecentPlaceCountJpaRepository,
    private val recentSortRepository: RecentPlaceSortJpaRepository,
    private val tagCatalogPort: PlaceTagCatalogQueryPort = PlaceTagCatalogQueryPort { PlaceTag.defaultDefinitions },
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : PlaceMapQueryPort {
    @Transactional(readOnly = true)
    override fun findInBounds(userId: Long, bounds: GeoBounds, groupIds: List<Long>?): List<MapPlaceView> {
        if (groupIds != null && groupIds.isEmpty()) return emptyList()
        val tagCatalog = tagCatalogPort.snapshot()
        return bookmarkRepository.findMapPlaces(
            userId = userId,
            northLatitude = bounds.northLatitude,
            westLongitude = bounds.westLongitude,
            southLatitude = bounds.southLatitude,
            eastLongitude = bounds.eastLongitude,
            filterByGroups = groupIds != null,
            groupIds = groupIds ?: listOf(0L),
        ).map { row ->
            MapPlaceView(
                id = row.id,
                name = row.name,
                city = row.city,
                category = row.category,
                categoryGroup = PlaceCategoryGroup.from(row.providerCategory),
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
    override fun countRecent(userId: Long, groupIds: List<Long>?): Long {
        if (groupIds != null && groupIds.isEmpty()) return 0L
        return recentCountRepository.countRecentPlaces(userId, groupIds != null, groupIds ?: listOf(0L))
    }

    @Transactional(readOnly = true)
    override fun findRecent(
        userId: Long,
        cursor: RecentPlaceCursor?,
        limit: Int,
        groupIds: List<Long>?,
        ordering: PlaceOrdering,
    ): List<RecentPlaceView> {
        if (groupIds != null && groupIds.isEmpty()) return emptyList()
        val tagCatalog = tagCatalogPort.snapshot()
        return recentRows(userId, cursor, limit, groupIds, ordering).map { row ->
            RecentPlaceView(
                bookmarkId = row.bookmarkId,
                bookmarkedAt = row.bookmarkedAt,
                id = row.placeId,
                name = row.name,
                city = row.city,
                address = row.address,
                category = row.category,
                categoryGroup = PlaceCategoryGroup.from(row.providerCategory),
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
                accessType = if (row.shareToken == null) PlaceAccessType.OWNED else PlaceAccessType.SHARED,
                shareToken = row.shareToken,
                distanceScore = if (ordering.sort == PlaceSort.NEAREST) row.distanceScore else null,
            )
        }
    }

    private fun recentRows(
        userId: Long,
        cursor: RecentPlaceCursor?,
        limit: Int,
        groupIds: List<Long>?,
        ordering: PlaceOrdering,
    ): List<RecentPlaceProjection> = if (ordering.sort == PlaceSort.LATEST) {
        bookmarkRepository.findRecentPlaces(
            userId = userId,
            cursorBookmarkedAt = cursor?.bookmarkedAt,
            cursorBookmarkId = cursor?.bookmarkId,
            limit = limit,
            filterByGroups = groupIds != null,
            groupIds = groupIds ?: listOf(0L),
        )
    } else {
        recentSortRepository.findSortedRecentPlaces(
            userId = userId,
            sort = ordering.sort.name,
            limit = limit,
            filterByGroups = groupIds != null,
            groupIds = groupIds ?: listOf(0L),
            latitude = ordering.latitude,
            longitude = ordering.longitude,
            cursorBookmarkedAt = cursor?.bookmarkedAt,
            cursorBookmarkId = cursor?.bookmarkId,
            cursorDistanceScore = cursor?.distanceScore,
        )
    }

    private fun String?.toDisplayTags(tagCatalog: PlaceTagCatalogSnapshot): List<String> = if (this.isNullOrBlank()) {
        emptyList()
    } else {
        tagCatalog.displayNames(objectMapper.readValue(this, Array<String>::class.java).asList())
    }
}
