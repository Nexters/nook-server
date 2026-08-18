package org.every.nook.api.infrastructure.persistence.save

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface SharedGroupContentJpaRepository : JpaRepository<UserSavedPostEntity, Long> {
    @Query(
        value = """
            SELECT EXISTS(
                SELECT 1
                FROM group_posts group_post
                INNER JOIN user_saved_posts saved_post ON saved_post.id = group_post.user_saved_post_id
                INNER JOIN user_saved_post_places saved_post_place
                    ON saved_post_place.user_saved_post_id = saved_post.id
                WHERE saved_post.user_id = :userId
                  AND group_post.group_id = :groupId
                  AND saved_post_place.place_id = :placeId
                  AND group_post.deleted_at IS NULL
                  AND saved_post.deleted_at IS NULL
            )
        """,
        nativeQuery = true,
    )
    fun existsPlaceInGroup(userId: Long, groupId: Long, placeId: Long): Long

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
              AND savedPost.id IN (
                  SELECT savedPostPlace.userSavedPostId
                  FROM UserSavedPostPlaceEntity savedPostPlace
                  WHERE savedPostPlace.placeId = :placeId
              )
        """,
    )
    fun findAllByUserIdAndGroupIdAndPlaceId(
        userId: Long,
        groupId: Long,
        placeId: Long,
        pageable: Pageable,
    ): Page<UserSavedPostEntity>
}
