package org.every.nook.api.application.place

import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.measure
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import java.time.Clock

class StorePlaceThumbnailUseCase(
    private val thumbnailProvider: PlaceThumbnailProvider,
    private val updatePort: PlaceThumbnailUpdatePort,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long, place: PlaceCandidate) {
        runCatching {
            updatePort.update(place.provider, place.externalPlaceId, PlaceThumbnailParsingStatus.PROCESSING)
            val supplement = metrics.measure(THUMBNAIL_FLOW, FETCH_STAGE, postId, null, clock) {
                thumbnailProvider.fetch(place)
            }
            metrics.measure(THUMBNAIL_FLOW, COMPLETE_STAGE, postId, null, clock) {
                updatePort.update(
                    place.provider,
                    place.externalPlaceId,
                    PlaceThumbnailParsingStatus.COMPLETED,
                    supplement,
                )
            }
        }.getOrElse { exception ->
            runCatching {
                updatePort.update(place.provider, place.externalPlaceId, PlaceThumbnailParsingStatus.FAILED)
            }.onFailure { statusException ->
                exception.addSuppressed(statusException)
            }
            throw exception
        }
    }

    private companion object {
        const val THUMBNAIL_FLOW = "place-thumbnail"
        const val FETCH_STAGE = "fetch"
        const val COMPLETE_STAGE = "complete"
    }
}
