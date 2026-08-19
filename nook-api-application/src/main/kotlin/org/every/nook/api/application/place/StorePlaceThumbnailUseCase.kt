package org.every.nook.api.application.place

import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.error
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.measure
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.slf4j.LoggerFactory
import java.time.Clock

class StorePlaceThumbnailUseCase(
    private val thumbnailProvider: PlaceThumbnailProvider,
    private val updatePort: PlaceThumbnailUpdatePort,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long, requests: List<PlaceThumbnailProvider.Request>) {
        require(requests.isNotEmpty()) { "thumbnail requests must not be empty" }
        val startedAt = clock.millis()
        requests.forEach { logger.info(event(postId, it.place, "place.thumbnail.started", FETCH_STAGE, "started")) }
        runCatching {
            requests.forEach { request ->
                updatePort.update(
                    request.place.provider,
                    request.place.externalPlaceId,
                    PlaceThumbnailParsingStatus.PROCESSING,
                )
            }
            val supplements = metrics.measure(THUMBNAIL_FLOW, FETCH_STAGE, postId, null, clock) {
                thumbnailProvider.fetchAll(requests)
            }
            require(supplements.size == requests.size) { "thumbnail provider returned an invalid result count" }
            requests.zip(supplements).forEach { (request, supplement) ->
                complete(postId, request, supplement, startedAt)
            }
        }.getOrElse { exception ->
            requests.forEach { request ->
                runCatching {
                    updatePort.update(
                        request.place.provider,
                        request.place.externalPlaceId,
                        PlaceThumbnailParsingStatus.FAILED,
                    )
                }.onFailure { statusException -> exception.addSuppressed(statusException) }
                logger.error(
                    event(postId, request.place, "place.thumbnail.failed", FETCH_STAGE, "failure", startedAt),
                    exception,
                )
            }
            throw exception
        }
    }

    private fun complete(
        postId: Long,
        request: PlaceThumbnailProvider.Request,
        supplement: PlaceSupplement?,
        startedAt: Long,
    ) {
        val status = if (supplement?.photoUrls.isNullOrEmpty()) {
            PlaceThumbnailParsingStatus.FAILED
        } else {
            PlaceThumbnailParsingStatus.COMPLETED
        }
        metrics.measure(THUMBNAIL_FLOW, COMPLETE_STAGE, postId, null, clock) {
            updatePort.update(request.place.provider, request.place.externalPlaceId, status, supplement)
        }
        val action = if (status == PlaceThumbnailParsingStatus.COMPLETED) {
            "place.thumbnail.completed"
        } else {
            "place.thumbnail.empty"
        }
        logger.info(
            event(
                postId,
                request.place,
                action,
                COMPLETE_STAGE,
                status.name.lowercase(),
                startedAt,
                mapOf(
                    "place.photo_count" to supplement?.photoUrls?.size,
                    "place.opening_hours_found" to (supplement?.openingHours != null),
                ),
            ),
        )
    }

    private fun event(
        postId: Long,
        place: PlaceCandidate,
        action: String,
        stage: String,
        outcome: String,
        startedAt: Long? = null,
        fields: Map<String, Any?> = emptyMap(),
    ) = ProcessingLogEvent(
        action = action,
        flow = THUMBNAIL_FLOW,
        stage = stage,
        outcome = outcome,
        sourcePostId = postId,
        durationMs = startedAt?.let { clock.millis() - it },
        fields = fields + mapOf(
            "provider.name" to place.provider,
            "place.external_id" to place.externalPlaceId,
        ),
    )

    private companion object {
        val logger = LoggerFactory.getLogger(StorePlaceThumbnailUseCase::class.java)
        const val THUMBNAIL_FLOW = "place-thumbnail"
        const val FETCH_STAGE = "fetch"
        const val COMPLETE_STAGE = "complete"
    }
}
