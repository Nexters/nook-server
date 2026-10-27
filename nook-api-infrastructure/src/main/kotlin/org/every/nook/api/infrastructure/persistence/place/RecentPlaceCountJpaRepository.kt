package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface RecentPlaceCountJpaRepository : Repository<UserPlaceBookmarkEntity, Long> {
    @Query(
        value = "SELECT COUNT(DISTINCT p.id) $RECENT_PLACE_ACCESS_SQL",
        nativeQuery = true,
    )
    fun countRecentPlaces(
        @Param("userId") userId: Long,
        @Param("filterByGroups") filterByGroups: Boolean,
        @Param("groupIds") groupIds: List<Long>,
    ): Long
}
