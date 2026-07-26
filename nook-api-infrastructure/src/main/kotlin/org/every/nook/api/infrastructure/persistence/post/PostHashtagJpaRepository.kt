package org.every.nook.api.infrastructure.persistence.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostHashtagJpaRepository : JpaRepository<PostHashtagEntity, Long> {
    fun findAllByPostIdOrderBySequenceAsc(postId: Long): List<PostHashtagEntity>
}
