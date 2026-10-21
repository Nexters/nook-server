package org.every.nook.api.infrastructure.persistence.push

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserPushTokenJpaRepository : JpaRepository<UserPushTokenEntity, Long> {
    fun findByToken(token: String): UserPushTokenEntity?

    fun findByUserIdAndToken(userId: Long, token: String): UserPushTokenEntity?

    @Query(
        """
            SELECT pushToken.userId AS userId, pushToken.token AS token, savedPost.id AS savedPostId
            FROM UserPushTokenEntity pushToken
            JOIN UserSavedPostEntity savedPost ON savedPost.userId = pushToken.userId
            WHERE pushToken.enabled = true
              AND savedPost.postId = :postId
              AND EXISTS (
                  SELECT groupPost.id FROM GroupPostEntity groupPost
                  JOIN GroupEntity userGroup ON userGroup.id = groupPost.groupId
                  WHERE groupPost.userSavedPostId = savedPost.id
                    AND userGroup.userId = savedPost.userId
              )
        """,
    )
    fun findPostProcessingTargets(@Param("postId") postId: Long): List<PostProcessingTarget>

    interface PostProcessingTarget {
        val userId: Long
        val savedPostId: Long
        val token: String
    }

    fun findAllByTokenIn(tokens: Collection<String>): List<UserPushTokenEntity>
}
