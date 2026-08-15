package org.every.nook.api.infrastructure.persistence.save

import org.every.nook.api.domain.post.PostContentParsingStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserSavedPostJpaRepository : JpaRepository<UserSavedPostEntity, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): UserSavedPostEntity?

    fun findByUserIdAndPostId(userId: Long, postId: Long): UserSavedPostEntity?

    fun findAllByUserId(userId: Long, pageable: Pageable): Page<UserSavedPostEntity>

    fun findAllByUserIdAndIdIn(userId: Long, ids: Collection<Long>): List<UserSavedPostEntity>

    @Query(
        """
            SELECT savedPost
            FROM UserSavedPostEntity savedPost
            WHERE savedPost.userId = :userId
              AND savedPost.id IN (
                  SELECT groupPost.userSavedPostId
                  FROM GroupPostEntity groupPost
                  WHERE groupPost.groupId = :groupId
              )
              AND EXISTS (
                  SELECT contentParsingJob.postId
                  FROM PostContentParsingJobEntity contentParsingJob
                  WHERE contentParsingJob.postId = savedPost.postId
                    AND contentParsingJob.status <> :excludedStatus
              )
        """,
    )
    fun findAllByUserIdAndGroupId(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long,
        @Param("excludedStatus") excludedStatus: PostContentParsingStatus,
        pageable: Pageable,
    ): Page<UserSavedPostEntity>

    @Query(
        value = """
            SELECT
                p.id AS id,
                p.name AS name,
                p.city AS city,
                p.address AS address,
                p.category AS category,
                p.latitude AS latitude,
                p.longitude AS longitude,
                COALESCE(
                    p.thumbnail_url,
                    (
                        SELECT post_media.media_url
                        FROM group_posts thumbnail_group_post
                        INNER JOIN user_saved_posts thumbnail_saved_post
                            ON thumbnail_saved_post.id = thumbnail_group_post.user_saved_post_id
                        INNER JOIN post_places thumbnail_post_place
                            ON thumbnail_post_place.post_id = thumbnail_saved_post.post_id
                        INNER JOIN post_media post_media
                            ON post_media.post_id = thumbnail_saved_post.post_id
                        WHERE thumbnail_group_post.group_id = :groupId
                          AND thumbnail_group_post.deleted_at IS NULL
                          AND thumbnail_saved_post.user_id = :userId
                          AND thumbnail_saved_post.deleted_at IS NULL
                          AND thumbnail_post_place.place_id = p.id
                          AND post_media.media_type = 'IMAGE'
                        ORDER BY
                            thumbnail_saved_post.created_at DESC,
                            thumbnail_saved_post.id DESC,
                            post_media.display_order ASC
                        LIMIT 1
                    )
                ) AS thumbnailUrl,
                CAST(p.representative_tags AS CHAR) AS representativeTags
            FROM user_groups user_group
            INNER JOIN group_posts group_post ON group_post.group_id = user_group.id
            INNER JOIN user_saved_posts saved_post ON saved_post.id = group_post.user_saved_post_id
            INNER JOIN post_places post_place ON post_place.post_id = saved_post.post_id
            INNER JOIN places p ON p.id = post_place.place_id
            WHERE user_group.id = :groupId
              AND user_group.user_id = :userId
              AND user_group.deleted_at IS NULL
              AND group_post.deleted_at IS NULL
              AND saved_post.user_id = :userId
              AND saved_post.deleted_at IS NULL
            GROUP BY
                p.id,
                p.name,
                p.city,
                p.address,
                p.category,
                p.latitude,
                p.longitude,
                p.thumbnail_url,
                CAST(p.representative_tags AS CHAR)
            ORDER BY MAX(saved_post.created_at) DESC, MAX(saved_post.id) DESC, p.id DESC
        """,
        countQuery = """
            SELECT COUNT(DISTINCT p.id)
            FROM user_groups user_group
            INNER JOIN group_posts group_post ON group_post.group_id = user_group.id
            INNER JOIN user_saved_posts saved_post ON saved_post.id = group_post.user_saved_post_id
            INNER JOIN post_places post_place ON post_place.post_id = saved_post.post_id
            INNER JOIN places p ON p.id = post_place.place_id
            WHERE user_group.id = :groupId
              AND user_group.user_id = :userId
              AND user_group.deleted_at IS NULL
              AND group_post.deleted_at IS NULL
              AND saved_post.user_id = :userId
              AND saved_post.deleted_at IS NULL
        """,
        nativeQuery = true,
    )
    fun findDistinctPlacesByUserIdAndGroupId(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long,
        pageable: Pageable,
    ): Page<GroupPlaceProjection>

    @Query(
        """
            SELECT savedPost
            FROM UserSavedPostEntity savedPost
            WHERE savedPost.userId = :userId
              AND savedPost.postId IN (
                  SELECT postPlace.postId
                  FROM PostPlaceEntity postPlace
                  WHERE postPlace.placeId = :placeId
              )
        """,
    )
    fun findAllByUserIdAndPlaceId(
        @Param("userId") userId: Long,
        @Param("placeId") placeId: Long,
        pageable: Pageable,
    ): Page<UserSavedPostEntity>

    @Query("SELECT DISTINCT savedPost.userId FROM UserSavedPostEntity savedPost WHERE savedPost.postId = :postId")
    fun findDistinctUserIdsByPostId(@Param("postId") postId: Long): List<Long>

    fun findAllByPostId(postId: Long): List<UserSavedPostEntity>

    @Modifying
    @Query(
        value = """
            UPDATE user_saved_posts
            SET deleted_at = NULL, updated_at = CURRENT_TIMESTAMP(6)
            WHERE user_id = :userId AND post_id = :postId
        """,
        nativeQuery = true,
    )
    fun restoreByUserIdAndPostId(userId: Long, postId: Long): Int
}

interface GroupPlaceProjection {
    val id: Long
    val name: String
    val city: String?
    val address: String
    val category: String?
    val latitude: java.math.BigDecimal
    val longitude: java.math.BigDecimal
    val thumbnailUrl: String?
    val representativeTags: String?
}
