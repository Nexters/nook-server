package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceImageUrlPort
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PostMediaImageUrlAdapter(private val mediaRepository: PostMediaJpaRepository) : PlaceImageUrlPort {
    @Transactional(readOnly = true)
    override fun findImageUrls(postId: Long): List<String> = mediaRepository
        .findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(postId, PostMedia.MediaType.IMAGE)
        .map { it.mediaUrl }
}
