package org.every.nook.api.infrastructure.persistence.place

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant

interface UserPlaceBookmarkJpaRepository : JpaRepository<UserPlaceBookmarkEntity, Long> {
    fun findAllByUserIdAndPlaceIdIn(userId: Long, placeIds: Collection<Long>): List<UserPlaceBookmarkEntity>

    fun findByUserIdAndPlaceId(userId: Long, placeId: Long): UserPlaceBookmarkEntity?

    fun existsByUserIdAndPlaceId(userId: Long, placeId: Long): Boolean

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                FROM user_saved_posts usp
                INNER JOIN user_saved_post_places uspp ON uspp.user_saved_post_id = usp.id
                WHERE usp.user_id = :userId
                  AND usp.deleted_at IS NULL
                  AND uspp.place_id = :placeId
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

    /**
     * 북마크를 만들면서 게시물 메모를 장소 메모의 초기값으로 심는다.
     * 이미 북마크가 있으면 IGNORE 되므로 사용자가 직접 쓴 메모를 덮어쓰지 않는다.
     */
    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO user_place_bookmarks (user_id, place_id, memo, created_at, updated_at)
            VALUES (:userId, :placeId, :memo, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
        """,
        nativeQuery = true,
    )
    fun insertIgnoreWithMemo(
        @Param("userId") userId: Long,
        @Param("placeId") placeId: Long,
        @Param("memo") memo: String?,
    ): Int

    fun deleteByUserIdAndPlaceId(userId: Long, placeId: Long): Long

    @Query(
        value = """
            SELECT
                p.id AS id,
                p.name AS name,
                p.city AS city,
                p.category AS category,
                p.latitude AS latitude,
                p.longitude AS longitude,
                p.thumbnail_url AS thumbnailUrl,
                p.thumbnail_parsing_status AS thumbnailParsingStatus,
                CAST(p.representative_tags AS CHAR) AS representativeTags,
                COALESCE(
                    (
                        SELECT user_group.color
                        FROM user_saved_posts color_saved_post
                        INNER JOIN user_saved_post_places color_saved_post_place
                            ON color_saved_post_place.user_saved_post_id = color_saved_post.id
                        INNER JOIN group_posts color_group_post
                            ON color_group_post.user_saved_post_id = color_saved_post.id
                        INNER JOIN user_groups user_group
                            ON user_group.id = color_group_post.group_id
                           AND user_group.user_id = color_saved_post.user_id
                        WHERE color_saved_post.user_id = upb.user_id
                          AND color_saved_post.deleted_at IS NULL
                          AND color_group_post.deleted_at IS NULL
                          AND user_group.deleted_at IS NULL
                          AND color_saved_post_place.place_id = p.id
                          AND color_saved_post.id = (
                              SELECT latest_saved_post.id
                              FROM user_saved_posts latest_saved_post
                              INNER JOIN user_saved_post_places latest_saved_post_place
                                  ON latest_saved_post_place.user_saved_post_id = latest_saved_post.id
                              WHERE latest_saved_post.user_id = upb.user_id
                                AND latest_saved_post.deleted_at IS NULL
                                AND latest_saved_post_place.place_id = p.id
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
              AND (
                EXISTS (
                  SELECT 1
                  FROM user_saved_posts usp
                  INNER JOIN user_saved_post_places uspp ON uspp.user_saved_post_id = usp.id
                  WHERE usp.user_id = upb.user_id
                    AND usp.deleted_at IS NULL
                    AND uspp.place_id = p.id
                )
                OR EXISTS (
                    SELECT 1
                    FROM shared_group_subscriptions subscription
                    INNER JOIN group_share_links share_link
                        ON share_link.id = subscription.share_link_id
                    INNER JOIN group_posts shared_group_post
                        ON shared_group_post.group_id = share_link.group_id
                    INNER JOIN user_groups shared_group ON shared_group.id = shared_group_post.group_id
                    INNER JOIN user_saved_posts shared_saved_post
                        ON shared_saved_post.id = shared_group_post.user_saved_post_id
                    INNER JOIN user_saved_post_places shared_saved_post_place
                        ON shared_saved_post_place.user_saved_post_id = shared_saved_post.id
                    WHERE subscription.member_id = upb.user_id
                      AND shared_saved_post_place.place_id = p.id
                      AND share_link.revoked_at IS NULL
                      AND (share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6))
                      AND shared_group_post.deleted_at IS NULL
                      AND shared_group.deleted_at IS NULL
                      AND shared_saved_post.deleted_at IS NULL
                )
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
                p.city AS city,
                p.address AS address,
                p.category AS category,
                p.latitude AS latitude,
                p.longitude AS longitude,
                CAST(p.representative_tags AS CHAR) AS representativeTags,
                p.thumbnail_url AS thumbnailUrl,
                p.thumbnail_parsing_status AS thumbnailParsingStatus,
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM user_saved_posts usp
                        INNER JOIN user_saved_post_places uspp ON uspp.user_saved_post_id = usp.id
                        WHERE usp.user_id = upb.user_id
                          AND usp.deleted_at IS NULL
                          AND uspp.place_id = p.id
                    ) THEN NULL
                    ELSE (
                        SELECT share_link.token_value
                        FROM shared_group_subscriptions subscription
                        INNER JOIN group_share_links share_link
                            ON share_link.id = subscription.share_link_id
                        INNER JOIN group_posts shared_group_post
                            ON shared_group_post.group_id = share_link.group_id
                        INNER JOIN user_groups shared_group ON shared_group.id = shared_group_post.group_id
                        INNER JOIN user_saved_posts shared_saved_post
                            ON shared_saved_post.id = shared_group_post.user_saved_post_id
                        INNER JOIN user_saved_post_places shared_saved_post_place
                            ON shared_saved_post_place.user_saved_post_id = shared_saved_post.id
                        WHERE subscription.member_id = upb.user_id
                          AND shared_saved_post_place.place_id = p.id
                          AND share_link.revoked_at IS NULL
                          AND (share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6))
                          AND shared_group_post.deleted_at IS NULL
                          AND shared_group.deleted_at IS NULL
                          AND shared_saved_post.deleted_at IS NULL
                        ORDER BY subscription.created_at DESC, subscription.id DESC
                        LIMIT 1
                    )
                END AS shareToken
            FROM user_place_bookmarks upb
            INNER JOIN places p ON p.id = upb.place_id
            WHERE upb.user_id = :userId
              AND (
                EXISTS (
                  SELECT 1
                  FROM user_saved_posts usp
                  INNER JOIN user_saved_post_places uspp ON uspp.user_saved_post_id = usp.id
                  WHERE usp.user_id = upb.user_id
                    AND usp.deleted_at IS NULL
                    AND uspp.place_id = p.id
                )
                OR EXISTS (
                    SELECT 1
                    FROM shared_group_subscriptions subscription
                    INNER JOIN group_share_links share_link
                        ON share_link.id = subscription.share_link_id
                    INNER JOIN group_posts shared_group_post
                        ON shared_group_post.group_id = share_link.group_id
                    INNER JOIN user_groups shared_group ON shared_group.id = shared_group_post.group_id
                    INNER JOIN user_saved_posts shared_saved_post
                        ON shared_saved_post.id = shared_group_post.user_saved_post_id
                    INNER JOIN user_saved_post_places shared_saved_post_place
                        ON shared_saved_post_place.user_saved_post_id = shared_saved_post.id
                    WHERE subscription.member_id = upb.user_id
                      AND shared_saved_post_place.place_id = p.id
                      AND share_link.revoked_at IS NULL
                      AND (share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6))
                      AND shared_group_post.deleted_at IS NULL
                      AND shared_group.deleted_at IS NULL
                      AND shared_saved_post.deleted_at IS NULL
                )
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

    @Query(
        value = """
            SELECT
                p.id AS id,
                p.name AS name,
                p.address AS address,
                p.category AS category,
                COALESCE(
                    p.thumbnail_url,
                    (
                        SELECT post_media.media_url
                        FROM user_saved_posts thumbnail_saved_post
                        INNER JOIN user_saved_post_places thumbnail_saved_post_place
                            ON thumbnail_saved_post_place.user_saved_post_id = thumbnail_saved_post.id
                        INNER JOIN post_media post_media
                            ON post_media.post_id = thumbnail_saved_post.post_id
                        WHERE thumbnail_saved_post.user_id = upb.user_id
                          AND thumbnail_saved_post.deleted_at IS NULL
                          AND thumbnail_saved_post_place.place_id = p.id
                          AND post_media.media_type = 'IMAGE'
                          AND (
                              :groupId IS NULL
                              OR EXISTS (
                                  SELECT 1
                                  FROM group_posts thumbnail_group_post
                                  INNER JOIN user_groups thumbnail_group
                                      ON thumbnail_group.id = thumbnail_group_post.group_id
                                  WHERE thumbnail_group_post.user_saved_post_id = thumbnail_saved_post.id
                                    AND thumbnail_group_post.group_id = :groupId
                                    AND thumbnail_group_post.deleted_at IS NULL
                                    AND thumbnail_group.user_id = upb.user_id
                                    AND thumbnail_group.deleted_at IS NULL
                              )
                          )
                        ORDER BY
                            thumbnail_saved_post.created_at DESC,
                            thumbnail_saved_post.id DESC,
                            post_media.display_order ASC
                        LIMIT 1
                    )
                ) AS thumbnailUrl
            FROM user_place_bookmarks upb
            INNER JOIN places p ON p.id = upb.place_id
            WHERE upb.user_id = :userId
              AND (
                  EXISTS (
                      SELECT 1
                      FROM user_saved_posts usp
                      INNER JOIN user_saved_post_places uspp ON uspp.user_saved_post_id = usp.id
                      WHERE usp.user_id = upb.user_id
                        AND usp.deleted_at IS NULL
                        AND uspp.place_id = p.id
                        AND (
                            :groupId IS NULL
                            OR EXISTS (
                                SELECT 1
                                FROM group_posts group_post
                                INNER JOIN user_groups user_group ON user_group.id = group_post.group_id
                                WHERE group_post.user_saved_post_id = usp.id
                                  AND group_post.group_id = :groupId
                                  AND group_post.deleted_at IS NULL
                                  AND user_group.user_id = upb.user_id
                                  AND user_group.deleted_at IS NULL
                            )
                        )
                  )
                  OR (
                      :groupId IS NULL
                      AND EXISTS (
                          SELECT 1
                          FROM shared_group_subscriptions subscription
                          INNER JOIN group_share_links share_link
                              ON share_link.id = subscription.share_link_id
                          INNER JOIN group_posts shared_group_post
                              ON shared_group_post.group_id = share_link.group_id
                          INNER JOIN user_groups shared_group
                              ON shared_group.id = shared_group_post.group_id
                          INNER JOIN user_saved_posts shared_saved_post
                              ON shared_saved_post.id = shared_group_post.user_saved_post_id
                          INNER JOIN user_saved_post_places shared_saved_post_place
                              ON shared_saved_post_place.user_saved_post_id = shared_saved_post.id
                          WHERE subscription.member_id = upb.user_id
                            AND shared_saved_post_place.place_id = p.id
                            AND share_link.revoked_at IS NULL
                            AND (share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6))
                            AND shared_group_post.deleted_at IS NULL
                            AND shared_group.deleted_at IS NULL
                            AND shared_saved_post.deleted_at IS NULL
                      )
                  )
              )
              AND (
                  p.name LIKE :pattern ESCAPE '!'
                  OR p.address LIKE :pattern ESCAPE '!'
                  OR p.category LIKE :pattern ESCAPE '!'
              )
            ORDER BY
                CASE
                    WHEN p.name LIKE :pattern ESCAPE '!' THEN 0
                    WHEN p.address LIKE :pattern ESCAPE '!' THEN 1
                    ELSE 2
                END,
                p.name ASC,
                p.id ASC
        """,
        countQuery = """
            SELECT COUNT(*)
            FROM user_place_bookmarks upb
            INNER JOIN places p ON p.id = upb.place_id
            WHERE upb.user_id = :userId
              AND (
                  EXISTS (
                      SELECT 1
                      FROM user_saved_posts usp
                      INNER JOIN user_saved_post_places uspp ON uspp.user_saved_post_id = usp.id
                      WHERE usp.user_id = upb.user_id
                        AND usp.deleted_at IS NULL
                        AND uspp.place_id = p.id
                        AND (
                            :groupId IS NULL
                            OR EXISTS (
                                SELECT 1
                                FROM group_posts group_post
                                INNER JOIN user_groups user_group ON user_group.id = group_post.group_id
                                WHERE group_post.user_saved_post_id = usp.id
                                  AND group_post.group_id = :groupId
                                  AND group_post.deleted_at IS NULL
                                  AND user_group.user_id = upb.user_id
                                  AND user_group.deleted_at IS NULL
                            )
                        )
                  )
                  OR (
                      :groupId IS NULL
                      AND EXISTS (
                          SELECT 1
                          FROM shared_group_subscriptions subscription
                          INNER JOIN group_share_links share_link
                              ON share_link.id = subscription.share_link_id
                          INNER JOIN group_posts shared_group_post
                              ON shared_group_post.group_id = share_link.group_id
                          INNER JOIN user_groups shared_group
                              ON shared_group.id = shared_group_post.group_id
                          INNER JOIN user_saved_posts shared_saved_post
                              ON shared_saved_post.id = shared_group_post.user_saved_post_id
                          INNER JOIN user_saved_post_places shared_saved_post_place
                              ON shared_saved_post_place.user_saved_post_id = shared_saved_post.id
                          WHERE subscription.member_id = upb.user_id
                            AND shared_saved_post_place.place_id = p.id
                            AND share_link.revoked_at IS NULL
                            AND (share_link.expires_at IS NULL OR share_link.expires_at > CURRENT_TIMESTAMP(6))
                            AND shared_group_post.deleted_at IS NULL
                            AND shared_group.deleted_at IS NULL
                            AND shared_saved_post.deleted_at IS NULL
                      )
                  )
              )
              AND (
                  p.name LIKE :pattern ESCAPE '!'
                  OR p.address LIKE :pattern ESCAPE '!'
                  OR p.category LIKE :pattern ESCAPE '!'
              )
        """,
        nativeQuery = true,
    )
    fun searchSavedPlaces(
        @Param("userId") userId: Long,
        @Param("pattern") pattern: String,
        @Param("groupId") groupId: Long?,
        pageable: Pageable,
    ): Page<SavedPlaceSearchProjection>

    @Query(
        value = """
            SELECT
                user_group.id AS id,
                user_group.name AS name,
                user_group.color AS color,
                COUNT(DISTINCT p.id) AS matchedPlaceCount
            FROM user_groups user_group
            INNER JOIN group_posts group_post
                ON group_post.group_id = user_group.id
               AND group_post.deleted_at IS NULL
            INNER JOIN user_saved_posts saved_post
                ON saved_post.id = group_post.user_saved_post_id
               AND saved_post.user_id = user_group.user_id
               AND saved_post.deleted_at IS NULL
            INNER JOIN user_saved_post_places saved_post_place
                ON saved_post_place.user_saved_post_id = saved_post.id
            INNER JOIN places p ON p.id = saved_post_place.place_id
            INNER JOIN user_place_bookmarks bookmark
                ON bookmark.place_id = p.id
               AND bookmark.user_id = user_group.user_id
            WHERE user_group.user_id = :userId
              AND user_group.deleted_at IS NULL
              AND (
                  p.name LIKE :pattern ESCAPE '!'
                  OR p.address LIKE :pattern ESCAPE '!'
                  OR p.category LIKE :pattern ESCAPE '!'
              )
            GROUP BY user_group.id, user_group.name, user_group.color
            ORDER BY user_group.id ASC
        """,
        nativeQuery = true,
    )
    fun findSavedPlaceSearchGroups(
        @Param("userId") userId: Long,
        @Param("pattern") pattern: String,
    ): List<SavedPlaceSearchGroupProjection>
}

interface MapPlaceProjection {
    val id: Long
    val name: String
    val city: String?
    val category: String?
    val latitude: BigDecimal
    val longitude: BigDecimal
    val color: String
    val thumbnailUrl: String?
    val thumbnailParsingStatus: String?
    val representativeTags: String?
}

interface RecentPlaceProjection {
    val bookmarkId: Long
    val bookmarkedAt: Instant
    val placeId: Long
    val name: String
    val city: String?
    val address: String
    val category: String?
    val latitude: BigDecimal
    val longitude: BigDecimal
    val thumbnailUrl: String?
    val thumbnailParsingStatus: String?
    val representativeTags: String?

    /** 내 저장 게시물로 접근할 수 있는 장소는 null이고, 공유 구독으로만 접근하는 장소만 활성 공유 토큰을 가진다. */
    val shareToken: String?
}

interface SavedPlaceSearchProjection {
    val id: Long
    val name: String
    val address: String
    val category: String?
    val thumbnailUrl: String?
}

interface SavedPlaceSearchGroupProjection {
    val id: Long
    val name: String
    val color: String
    val matchedPlaceCount: Long
}
