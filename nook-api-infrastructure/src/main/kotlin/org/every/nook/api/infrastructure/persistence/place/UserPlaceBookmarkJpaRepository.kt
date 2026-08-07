package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant

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
                  AND usp.deleted_at IS NULL
                  AND pp.place_id = :placeId
            )
        """,
        nativeQuery = true,
    )
    fun isAccessible(@Param("userId") userId: Long, @Param("placeId") placeId: Long): Long

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

    @Query(
        value = """
            SELECT
                p.id AS id,
                p.name AS name,
                p.latitude AS latitude,
                p.longitude AS longitude,
                p.thumbnail_url AS thumbnailUrl,
                CAST(p.representative_tags AS CHAR) AS representativeTags,
                COALESCE(
                    (
                        SELECT user_group.color
                        FROM user_saved_posts color_saved_post
                        INNER JOIN post_places color_post_place
                            ON color_post_place.post_id = color_saved_post.post_id
                        INNER JOIN group_posts color_group_post
                            ON color_group_post.user_saved_post_id = color_saved_post.id
                        INNER JOIN user_groups user_group
                            ON user_group.id = color_group_post.group_id
                           AND user_group.user_id = color_saved_post.user_id
                        WHERE color_saved_post.user_id = upb.user_id
                          AND color_saved_post.deleted_at IS NULL
                          AND color_group_post.deleted_at IS NULL
                          AND user_group.deleted_at IS NULL
                          AND color_post_place.place_id = p.id
                          AND color_saved_post.id = (
                              SELECT latest_saved_post.id
                              FROM user_saved_posts latest_saved_post
                              INNER JOIN post_places latest_post_place
                                  ON latest_post_place.post_id = latest_saved_post.post_id
                              WHERE latest_saved_post.user_id = upb.user_id
                                AND latest_saved_post.deleted_at IS NULL
                                AND latest_post_place.place_id = p.id
                              ORDER BY latest_saved_post.created_at DESC, latest_saved_post.id DESC
                              LIMIT 1
                          )
                        ORDER BY
                            color_group_post.created_at DESC,
                            color_group_post.id DESC
                        LIMIT 1
                    ),
                    'YELLOW'
                ) AS color
            FROM user_place_bookmarks upb
            INNER JOIN places p ON p.id = upb.place_id
            WHERE upb.user_id = :userId
              AND p.latitude BETWEEN :southLatitude AND :northLatitude
              AND p.longitude BETWEEN :westLongitude AND :eastLongitude
              AND EXISTS (
                  SELECT 1
                  FROM user_saved_posts usp
                  INNER JOIN post_places pp ON pp.post_id = usp.post_id
                  WHERE usp.user_id = upb.user_id
                    AND usp.deleted_at IS NULL
                    AND pp.place_id = p.id
              )
        """,
        nativeQuery = true,
    )
    fun findMapPlaces(
        @Param("userId") userId: Long,
        @Param("northLatitude") northLatitude: BigDecimal,
        @Param("westLongitude") westLongitude: BigDecimal,
        @Param("southLatitude") southLatitude: BigDecimal,
        @Param("eastLongitude") eastLongitude: BigDecimal,
    ): List<MapPlaceProjection>

    @Query(
        value = """
            SELECT
                upb.id AS bookmarkId,
                upb.created_at AS bookmarkedAt,
                p.id AS placeId,
                p.name AS name,
                p.address AS address,
                p.category AS category,
                p.latitude AS latitude,
                p.longitude AS longitude,
                CAST(p.representative_tags AS CHAR) AS representativeTags,
                COALESCE(
                    p.thumbnail_url,
                    (
                    SELECT pm.media_url
                    FROM user_saved_posts usp_image
                    INNER JOIN post_places pp_image ON pp_image.post_id = usp_image.post_id
                    INNER JOIN post_media pm ON pm.post_id = usp_image.post_id
                    WHERE usp_image.user_id = upb.user_id
                      AND usp_image.deleted_at IS NULL
                      AND pp_image.place_id = p.id
                      AND pm.media_type = 'IMAGE'
                    ORDER BY usp_image.created_at DESC, usp_image.id DESC, pm.display_order ASC
                    LIMIT 1
                    )
                ) AS thumbnailUrl
            FROM user_place_bookmarks upb
            INNER JOIN places p ON p.id = upb.place_id
            WHERE upb.user_id = :userId
              AND EXISTS (
                  SELECT 1
                  FROM user_saved_posts usp
                  INNER JOIN post_places pp ON pp.post_id = usp.post_id
                  WHERE usp.user_id = upb.user_id
                    AND usp.deleted_at IS NULL
                    AND pp.place_id = p.id
              )
              AND (
                  :cursorBookmarkedAt IS NULL
                  OR upb.created_at < :cursorBookmarkedAt
                  OR (upb.created_at = :cursorBookmarkedAt AND upb.id < :cursorBookmarkId)
              )
            ORDER BY upb.created_at DESC, upb.id DESC
            LIMIT :limit
        """,
        nativeQuery = true,
    )
    fun findRecentPlaces(
        @Param("userId") userId: Long,
        @Param("cursorBookmarkedAt") cursorBookmarkedAt: Instant?,
        @Param("cursorBookmarkId") cursorBookmarkId: Long?,
        @Param("limit") limit: Int,
    ): List<RecentPlaceProjection>
}

interface MapPlaceProjection {
    val id: Long
    val name: String
    val latitude: BigDecimal
    val longitude: BigDecimal
    val color: String
    val thumbnailUrl: String?
    val representativeTags: String?
}

interface RecentPlaceProjection {
    val bookmarkId: Long
    val bookmarkedAt: Instant
    val placeId: Long
    val name: String
    val address: String
    val category: String?
    val latitude: BigDecimal
    val longitude: BigDecimal
    val thumbnailUrl: String?
    val representativeTags: String?
}
