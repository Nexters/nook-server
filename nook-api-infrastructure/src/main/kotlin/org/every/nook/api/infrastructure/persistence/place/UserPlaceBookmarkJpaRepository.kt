package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserPlaceBookmarkJpaRepository : JpaRepository<UserPlaceBookmarkEntity, Long> {
    fun findAllByUserIdAndPlaceIdIn(userId: Long, placeIds: Collection<Long>): List<UserPlaceBookmarkEntity>

    fun existsByUserIdAndPlaceId(userId: Long, placeId: Long): Boolean

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                FROM user_saved_posts usp
                INNER JOIN post_places pp ON pp.post_id = usp.post_id
                WHERE usp.user_id = :userId
                  AND pp.place_id = :placeId
            )
        """,
        nativeQuery = true,
    )
    fun isAccessible(@Param("userId") userId: Long, @Param("placeId") placeId: Long): Boolean

    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO user_place_bookmarks (user_id, place_id, created_at, updated_at)
            VALUES (:userId, :placeId, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        """,
        nativeQuery = true,
    )
    fun insertIgnore(@Param("userId") userId: Long, @Param("placeId") placeId: Long): Int

    fun deleteByUserIdAndPlaceId(userId: Long, placeId: Long): Long
}
