package org.every.nook.api.application.place

import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.measure
import java.time.Clock

class StorePlaceThumbnailUseCase(
    private val thumbnailProvider: PlaceThumbnailProvider,
    private val updatePort: PlaceThumbnailUpdatePort,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long, place: PlaceCandidate) {
        val thumbnailUrl = metrics.measure(THUMBNAIL_FLOW, FETCH_STAGE, postId, null, clock) {
            thumbnailProvider.fetchThumbnailUrl(place)
        } ?: return
        metrics.measure(THUMBNAIL_FLOW, COMPLETE_STAGE, postId, null, clock) {
            updatePort.update(place.provider, place.externalPlaceId, thumbnailUrl)
        }
    }

    private companion object {
        const val THUMBNAIL_FLOW = "place-thumbnail"
        const val FETCH_STAGE = "fetch"
        const val COMPLETE_STAGE = "complete"
    }
}
