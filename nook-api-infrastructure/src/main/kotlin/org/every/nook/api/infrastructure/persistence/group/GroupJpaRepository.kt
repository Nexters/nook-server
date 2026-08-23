package org.every.nook.api.infrastructure.persistence.group

import jakarta.persistence.LockModeType
import org.every.nook.api.domain.group.GroupColor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface GroupJpaRepository : JpaRepository<GroupEntity, Long> {
    @Query(
        value = """
            SELECT
                user_group.id AS id,
                user_group.name AS name,
                user_group.color AS color,
                COUNT(saved_post.id) AS postCount
            FROM user_groups user_group
            LEFT JOIN group_posts group_post ON group_post.group_id = user_group.id
                AND group_post.deleted_at IS NULL
            LEFT JOIN user_saved_posts saved_post ON saved_post.id = group_post.user_saved_post_id
                AND saved_post.deleted_at IS NULL
            WHERE user_group.user_id = :userId
              AND user_group.deleted_at IS NULL
            GROUP BY user_group.id, user_group.name, user_group.color
            ORDER BY user_group.id
        """,
        nativeQuery = true,
    )
    fun findAllSummaries(userId: Long): List<GroupSummaryProjection>

    @Query(
        value = """
            SELECT
                ranked_thumbnail.group_id AS groupId,
                ranked_thumbnail.post_media_url AS postMediaUrl,
                ranked_thumbnail.place_thumbnail_url AS placeThumbnailUrl
            FROM (
                SELECT
                    thumbnail_candidate.group_id,
                    thumbnail_candidate.post_media_url,
                    thumbnail_candidate.place_thumbnail_url,
                    ROW_NUMBER() OVER (
                        PARTITION BY thumbnail_candidate.group_id
                        ORDER BY thumbnail_candidate.saved_at DESC, thumbnail_candidate.saved_post_id DESC
                    ) AS thumbnail_order
                FROM (
                    SELECT
                        group_post.group_id,
                        saved_post.id AS saved_post_id,
                        saved_post.created_at AS saved_at,
                        CASE
                            WHEN post_media.media_type = 'IMAGE' THEN post_media.media_url
                            WHEN post_media.media_type = 'VIDEO' THEN post_media.thumbnail_url
                        END AS post_media_url,
                        (
                            SELECT place.thumbnail_url
                            FROM user_saved_post_places saved_post_place
                          INNER JOIN places place ON place.id = saved_post_place.place_id
                          WHERE saved_post_place.user_saved_post_id = saved_post.id
                            AND place.thumbnail_url IS NOT NULL
                            ORDER BY saved_post_place.display_order ASC
                            LIMIT 1
                        ) AS place_thumbnail_url
                    FROM user_groups user_group
                    INNER JOIN group_posts group_post ON group_post.group_id = user_group.id
                    INNER JOIN user_saved_posts saved_post ON saved_post.id = group_post.user_saved_post_id
                    INNER JOIN posts post ON post.id = saved_post.post_id
                    LEFT JOIN post_media post_media
                        ON post_media.post_id = saved_post.post_id
                        AND (
                            post_media.media_type = 'IMAGE'
                            OR (post_media.media_type = 'VIDEO' AND post_media.thumbnail_url IS NOT NULL)
                        )
                    LEFT JOIN post_media earlier_media
                        ON earlier_media.post_id = post_media.post_id
                        AND (
                            earlier_media.media_type = 'IMAGE'
                            OR (earlier_media.media_type = 'VIDEO' AND earlier_media.thumbnail_url IS NOT NULL)
                        )
                        AND earlier_media.display_order < post_media.display_order
                    WHERE user_group.user_id = :userId
                      AND user_group.deleted_at IS NULL
                      AND group_post.deleted_at IS NULL
                      AND saved_post.deleted_at IS NULL
                      AND saved_post.user_id = :userId
                      AND earlier_media.id IS NULL
                ) thumbnail_candidate
                WHERE thumbnail_candidate.post_media_url IS NOT NULL
                   OR thumbnail_candidate.place_thumbnail_url IS NOT NULL
            ) ranked_thumbnail
            WHERE ranked_thumbnail.thumbnail_order <= 3
            ORDER BY ranked_thumbnail.group_id, ranked_thumbnail.thumbnail_order
        """,
        nativeQuery = true,
    )
    fun findRecentThumbnailUrls(@Param("userId") userId: Long): List<GroupThumbnailProjection>

    fun findByIdAndUserId(id: Long, userId: Long): GroupEntity?

    fun findAllByIdInAndUserId(ids: Collection<Long>, userId: Long): List<GroupEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT userGroup FROM GroupEntity userGroup WHERE userGroup.id = :id AND userGroup.userId = :userId")
    fun findByIdAndUserIdForUpdate(id: Long, userId: Long): GroupEntity?

    fun findAllByUserIdAndIdIn(userId: Long, ids: Set<Long>): List<GroupEntity>

    fun existsByIdAndUserId(id: Long, userId: Long): Boolean

    @Modifying
    @Transactional
    @Query(
        """
            UPDATE GroupEntity userGroup
            SET userGroup.name = :name, userGroup.color = :color
            WHERE userGroup.id = :id AND userGroup.userId = :userId
        """,
    )
    fun updateByIdAndUserId(id: Long, userId: Long, name: String, color: GroupColor): Int

    fun deleteByIdAndUserId(id: Long, userId: Long): Long
}

interface GroupSummaryProjection {
    val id: Long
    val name: String
    val color: GroupColor
    val postCount: Long
}

interface GroupThumbnailProjection {
    val groupId: Long
    val postMediaUrl: String?
    val placeThumbnailUrl: String?
}
