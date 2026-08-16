package org.every.nook.api.infrastructure.persistence.save

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param

interface UserSavedPostLockJpaRepository : Repository<UserSavedPostEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
            SELECT savedPost
            FROM UserSavedPostEntity savedPost
            WHERE savedPost.id = :id AND savedPost.userId = :userId
        """,
    )
    fun findByIdAndUserIdForUpdate(@Param("id") id: Long, @Param("userId") userId: Long): UserSavedPostEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT savedPost FROM UserSavedPostEntity savedPost WHERE savedPost.postId = :postId")
    fun findAllByPostIdForUpdate(@Param("postId") postId: Long): List<UserSavedPostEntity>
}
