package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant

interface RecentPlaceSortJpaRepository : Repository<UserPlaceBookmarkEntity, Long> {
    @Query(
        value = """
            SELECT ranked.* FROM (
                SELECT source.*, $PLACE_DISTANCE_SCORE_SQL AS distanceScore
                FROM ($RECENT_PLACE_SELECT_SQL) source
                INNER JOIN places p ON p.id = source.placeId
            ) ranked
            WHERE :cursorBookmarkedAt IS NULL
                OR (:sort = 'OLDEST' AND (
                    bookmarkedAt > :cursorBookmarkedAt
                    OR (bookmarkedAt = :cursorBookmarkedAt AND bookmarkId < :cursorBookmarkId)
                ))
                OR (:sort = 'NEAREST' AND (
                    distanceScore > :cursorDistanceScore
                    OR (distanceScore = :cursorDistanceScore AND (
                        bookmarkedAt < :cursorBookmarkedAt
                        OR (bookmarkedAt = :cursorBookmarkedAt AND bookmarkId < :cursorBookmarkId)
                    ))
                ))
            ORDER BY
                CASE WHEN :sort = 'NEAREST' THEN distanceScore END ASC,
                CASE WHEN :sort = 'OLDEST' THEN bookmarkedAt END ASC,
                CASE WHEN :sort = 'NEAREST' THEN bookmarkedAt END DESC,
                bookmarkId DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findSortedRecentPlaces(
        @Param("userId") userId: Long,
        @Param("sort") sort: String,
        @Param("limit") limit: Int,
        @Param("filterByGroups") filterByGroups: Boolean,
        @Param("groupIds") groupIds: List<Long>,
        @Param("latitude") latitude: BigDecimal? = null,
        @Param("longitude") longitude: BigDecimal? = null,
        @Param("cursorBookmarkedAt") cursorBookmarkedAt: Instant? = null,
        @Param("cursorBookmarkId") cursorBookmarkId: Long? = null,
        @Param("cursorDistanceScore") cursorDistanceScore: Double? = null,
    ): List<RecentPlaceProjection>
}
