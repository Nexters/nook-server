package org.every.nook.api.infrastructure.persistence.place

import org.every.nook.api.application.place.PlaceImageReadinessPort
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository
import org.every.nook.api.infrastructure.storage.MediaStorageProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PostMediaOcrReadinessAdapter(
    private val mediaRepository: PostMediaJpaRepository,
    private val mediaStoragePropertiesProvider: ObjectProvider<MediaStorageProperties>,
) : PlaceImageReadinessPort {
    @Transactional(readOnly = true)
    override fun areImageUrlsReadyForOcr(postId: Long): Boolean {
        val mediaStorageProperties = mediaStoragePropertiesProvider.getIfAvailable()
        return mediaStorageProperties?.takeIf { it.enabled }
            ?.let { properties -> areStoredMediaUrls(postId, properties) }
            ?: true
    }

    private fun areStoredMediaUrls(postId: Long, properties: MediaStorageProperties): Boolean {
        val imageUrls = mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(
            postId,
            PostMedia.MediaType.IMAGE,
        ).map { it.mediaUrl }
        val storedMediaUrlPrefix = "${properties.cloudFrontBaseUrl.trimEnd('/')}/"
        return imageUrls.all { it.startsWith(storedMediaUrlPrefix) }
    }
}
