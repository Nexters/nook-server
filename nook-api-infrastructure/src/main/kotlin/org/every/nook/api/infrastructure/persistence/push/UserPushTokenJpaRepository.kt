package org.every.nook.api.infrastructure.persistence.push

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserPushTokenJpaRepository : JpaRepository<UserPushTokenEntity, Long> {
    fun findByToken(token: String): UserPushTokenEntity?

    fun findByUserIdAndToken(userId: Long, token: String): UserPushTokenEntity?

    @Query(
        """
            SELECT pushToken
            FROM UserPushTokenEntity pushToken
            WHERE pushToken.enabled = true
              AND pushToken.userId IN (
                  SELECT DISTINCT savedPost.userId
                  FROM UserSavedPostEntity savedPost
                  WHERE savedPost.postId = :postId
              )
        """,
    )
    fun findAllEnabledByPostId(@Param("postId") postId: Long): List<UserPushTokenEntity>

    fun findAllByTokenIn(tokens: Collection<String>): List<UserPushTokenEntity>
}
