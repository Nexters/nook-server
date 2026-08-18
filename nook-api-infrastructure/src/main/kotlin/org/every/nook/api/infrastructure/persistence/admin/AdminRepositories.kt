package org.every.nook.api.infrastructure.persistence.admin

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AdminAuditLogJpaRepository : JpaRepository<AdminAuditLogEntity, Long> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AdminAuditLogEntity>

    fun findAllByTargetTypeAndTargetIdOrderByCreatedAtDesc(
        targetType: String,
        targetId: String,
        pageable: Pageable,
    ): Page<AdminAuditLogEntity>

    fun findAllByTargetTypeOrderByCreatedAtDesc(targetType: String, pageable: Pageable): Page<AdminAuditLogEntity>
}

interface PostPlaceReviewJpaRepository : JpaRepository<PostPlaceReviewEntity, Long> {
    fun findByPostId(postId: Long): PostPlaceReviewEntity?

    fun existsByPostId(postId: Long): Boolean

    fun findAllByPostIdIn(postIds: Collection<Long>): List<PostPlaceReviewEntity>
}
