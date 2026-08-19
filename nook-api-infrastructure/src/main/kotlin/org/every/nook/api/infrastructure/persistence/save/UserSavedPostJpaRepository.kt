package org.every.nook.api.infrastructure.persistence.save

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
        """,
    )
    fun findAllByUserIdAndGroupId(
        @Param("userId") userId: Long,
        @Param("groupId") groupId: Long,
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
                p.thumbnail_url AS thumbnailUrl,
                p.thumbnail_parsing_status AS thumbnailParsingStatus,
                CAST(p.representative_tags AS CHAR) AS representativeTags
            FROM user_groups user_group
            INNER JOIN group_posts group_post ON group_post.group_id = user_group.id
            INNER JOIN user_saved_posts saved_post ON saved_post.id = group_post.user_saved_post_id
            INNER JOIN user_saved_post_places saved_post_place
                ON saved_post_place.user_saved_post_id = saved_post.id
            INNER JOIN places p ON p.id = saved_post_place.place_id
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
                p.thumbnail_parsing_status,
                CAST(p.representative_tags AS CHAR)
            ORDER BY MAX(saved_post.created_at) DESC, MAX(saved_post.id) DESC, p.id DESC
        """,
        countQuery = """
            SELECT COUNT(DISTINCT p.id)
            FROM user_groups user_group
            INNER JOIN group_posts group_post ON group_post.group_id = user_group.id
            INNER JOIN user_saved_posts saved_post ON saved_post.id = group_post.user_saved_post_id
            INNER JOIN user_saved_post_places saved_post_place
                ON saved_post_place.user_saved_post_id = saved_post.id
            INNER JOIN places p ON p.id = saved_post_place.place_id
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
              AND savedPost.id IN (
                  SELECT savedPostPlace.userSavedPostId
                  FROM UserSavedPostPlaceEntity savedPostPlace
                  WHERE savedPostPlace.placeId = :placeId
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
    val thumbnailParsingStatus: String?
    val representativeTags: String?
}
