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
