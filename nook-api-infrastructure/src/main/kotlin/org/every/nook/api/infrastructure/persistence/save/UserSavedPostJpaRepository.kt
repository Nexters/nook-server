package org.every.nook.api.infrastructure.persistence.save

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserSavedPostJpaRepository : JpaRepository<UserSavedPostEntity, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): UserSavedPostEntity?

    fun findAllByUserId(userId: Long, pageable: Pageable): Page<UserSavedPostEntity>

    @Query("SELECT DISTINCT savedPost.userId FROM UserSavedPostEntity savedPost WHERE savedPost.postId = :postId")
    fun findDistinctUserIdsByPostId(@Param("postId") postId: Long): List<Long>
}
