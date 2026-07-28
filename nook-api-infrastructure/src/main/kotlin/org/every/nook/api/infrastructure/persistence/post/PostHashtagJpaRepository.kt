package org.every.nook.api.infrastructure.persistence.post

import org.springframework.data.jpa.repository.JpaRepository

interface PostHashtagJpaRepository : JpaRepository<PostHashtagEntity, Long> {
    fun deleteAllByPostId(postId: Long)

    fun findAllByPostIdOrderBySequenceAsc(postId: Long): List<PostHashtagEntity>

    fun findAllByPostIdInOrderByPostIdAscSequenceAsc(postIds: Collection<Long>): List<PostHashtagEntity>
}
