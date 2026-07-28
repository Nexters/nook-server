package org.every.nook.api.infrastructure.persistence.post

import org.every.nook.api.domain.post.PostMedia
import org.springframework.data.jpa.repository.JpaRepository

interface PostMediaJpaRepository : JpaRepository<PostMediaEntity, Long> {
    fun deleteAllByPostId(postId: Long)

    fun findAllByPostIdInOrderByPostIdAscSequenceAsc(postIds: Collection<Long>): List<PostMediaEntity>

    fun findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(
        postId: Long,
        mediaType: PostMedia.MediaType,
    ): List<PostMediaEntity>
}
