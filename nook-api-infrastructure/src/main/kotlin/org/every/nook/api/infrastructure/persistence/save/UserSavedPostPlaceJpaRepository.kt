package org.every.nook.api.infrastructure.persistence.save

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserSavedPostPlaceJpaRepository : JpaRepository<UserSavedPostPlaceEntity, Long> {
    fun findByUserSavedPostIdAndPlaceId(userSavedPostId: Long, placeId: Long): UserSavedPostPlaceEntity?

    fun findAllByUserSavedPostIdOrderBySequenceAsc(userSavedPostId: Long): List<UserSavedPostPlaceEntity>

    fun findAllByUserSavedPostIdInOrderByUserSavedPostIdAscSequenceAsc(
        userSavedPostIds: Collection<Long>,
    ): List<UserSavedPostPlaceEntity>

    fun deleteByUserSavedPostIdAndPlaceId(userSavedPostId: Long, placeId: Long): Long

    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO user_saved_post_places (
                user_saved_post_id,
                place_id,
                display_order,
                thumbnail_url,
                created_at,
                updated_at
            )
            SELECT
                :userSavedPostId,
                post_place.place_id,
                existing_order.max_display_order +
                    ROW_NUMBER() OVER (ORDER BY post_place.display_order, post_place.id),
                NULL,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            FROM post_places post_place
            CROSS JOIN (
                SELECT COALESCE(MAX(saved_post_place.display_order), -1) AS max_display_order
                FROM user_saved_post_places saved_post_place
                WHERE saved_post_place.user_saved_post_id = :userSavedPostId
            ) existing_order
            WHERE post_place.post_id = :postId
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_saved_post_places existing_place
                  WHERE existing_place.user_saved_post_id = :userSavedPostId
                    AND existing_place.place_id = post_place.place_id
              )
        """,
        nativeQuery = true,
    )
    fun insertAllFromPost(@Param("userSavedPostId") userSavedPostId: Long, @Param("postId") postId: Long): Int

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                FROM user_saved_post_places saved_post_place
                INNER JOIN user_saved_posts saved_post
                    ON saved_post.id = saved_post_place.user_saved_post_id
                WHERE saved_post.post_id = :postId
                  AND saved_post_place.place_id = :placeId
            )
        """,
        nativeQuery = true,
    )
    fun existsByPostIdAndPlaceId(@Param("postId") postId: Long, @Param("placeId") placeId: Long): Long

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1
                FROM user_saved_post_places saved_post_place
                INNER JOIN user_saved_posts saved_post
                    ON saved_post.id = saved_post_place.user_saved_post_id
                WHERE saved_post.user_id = :userId
                  AND saved_post.deleted_at IS NULL
                  AND saved_post_place.place_id = :placeId
            )
        """,
        nativeQuery = true,
    )
    fun existsActiveByUserIdAndPlaceId(@Param("userId") userId: Long, @Param("placeId") placeId: Long): Long
}
