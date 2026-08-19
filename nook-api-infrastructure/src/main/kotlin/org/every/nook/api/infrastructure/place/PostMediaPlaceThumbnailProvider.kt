package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.every.nook.api.infrastructure.persistence.post.PostMediaJpaRepository

class PostMediaPlaceThumbnailProvider(
    private val mediaRepository: PostMediaJpaRepository,
    private val mediaStorage: PostMediaStoragePort,
    private val storedMediaBaseUrl: String?,
    private val obsoleteFixedThumbnailUrl: String,
) : PlaceThumbnailProvider {
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? {
        val sourcePostId = request.sourcePostId
        val sourceMediaSequence = request.sourceMediaSequence
        if (sourcePostId == null || sourceMediaSequence == null) return null
        val images = mediaRepository.findFirst20ByPostIdAndMediaTypeOrderBySequenceAsc(
            sourcePostId,
            PostMedia.MediaType.IMAGE,
        ).map { media ->
            PostMedia(
                type = media.mediaType,
                url = media.mediaUrl,
                sequence = media.sequence,
            )
        }
        val selected = images.getOrNull(sourceMediaSequence) ?: return null
        val stored = if (selected.url.isStoredMedia()) selected else mediaStorage.store(selected)
        return PlaceSupplement(
            openingHours = null,
            photoUrls = listOf(stored.url),
            replaceThumbnailUrl = obsoleteFixedThumbnailUrl,
        )
    }

    private fun String.isStoredMedia(): Boolean = storedMediaBaseUrl
        ?.trimEnd('/')
        ?.takeIf(String::isNotBlank)
        ?.let { baseUrl -> startsWith("$baseUrl/") }
        ?: false
}
