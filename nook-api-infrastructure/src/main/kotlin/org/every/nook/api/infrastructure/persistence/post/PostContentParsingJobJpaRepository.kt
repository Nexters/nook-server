package org.every.nook.api.infrastructure.persistence.post

import jakarta.persistence.LockModeType
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PostContentParsingJobJpaRepository : JpaRepository<PostContentParsingJobEntity, Long> {
    fun findByPostId(postId: Long): PostContentParsingJobEntity?

    fun findAllByPostIdIn(postIds: Collection<Long>): List<PostContentParsingJobEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM PostContentParsingJobEntity job WHERE job.postId = :postId")
    fun findByPostIdForUpdate(@Param("postId") postId: Long): PostContentParsingJobEntity?

    fun findAllByStatusIn(statuses: Collection<PostContentParsingStatus>): List<PostContentParsingJobEntity>

    fun findAllByStatusInOrderByNextAttemptAtAsc(
        statuses: Collection<PostContentParsingStatus>,
        pageable: Pageable,
    ): List<PostContentParsingJobEntity>
}
