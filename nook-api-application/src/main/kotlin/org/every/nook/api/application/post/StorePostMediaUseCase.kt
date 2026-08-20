package org.every.nook.api.application.post

import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.post.port.UpdatePostMediaUrlPort
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.measure
import org.every.nook.api.domain.post.PostMedia
import java.time.Clock

class StorePostMediaUseCase(
    private val mediaStorage: PostMediaStoragePort,
    private val updateMediaUrl: UpdatePostMediaUrlPort,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long, command: Command) {
        val media = PostMedia(
            type = PostMedia.MediaType.valueOf(command.mediaType),
            url = command.sourceUrl,
            sequence = command.sequence,
        )
        val stored = metrics.measure(MEDIA_FLOW, STORE_STAGE, postId, null, clock) {
            mediaStorage.store(media)
        }
        val storedThumbnailUrl = command.sourceThumbnailUrl?.let { thumbnailUrl ->
            metrics.measure(MEDIA_FLOW, STORE_THUMBNAIL_STAGE, postId, null, clock) {
                mediaStorage.store(
                    PostMedia(PostMedia.MediaType.IMAGE, thumbnailUrl, command.sequence),
                ).url
            }
        }
        metrics.measure(MEDIA_FLOW, COMPLETE_STAGE, postId, null, clock) {
            updateMediaUrl.update(
                postId,
                media.sequence,
                media.url,
                stored.url,
                command.sourceThumbnailUrl,
                storedThumbnailUrl,
            )
        }
    }

    data class Command(
        val mediaType: String,
        val sourceUrl: String,
        val sequence: Int,
        val sourceThumbnailUrl: String? = null,
    )

    private companion object {
        const val MEDIA_FLOW = "post-media"
        const val STORE_STAGE = "store"
        const val STORE_THUMBNAIL_STAGE = "store-thumbnail"
        const val COMPLETE_STAGE = "complete"
    }
}
