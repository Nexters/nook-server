package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.slf4j.LoggerFactory

class StorePlaceTagsUseCase(
    private val sourcePort: PlaceTagSourcePort,
    private val extractor: PlaceTagExtractor,
    private val updatePort: PlaceTagUpdatePort,
) {
    operator fun invoke(event: PlaceTagsRequestedEvent) {
        val source = sourcePort.find(event.postId) ?: run {
            logger.info(
                logEvent(
                    event,
                    "place.tags.skipped",
                    "load_source",
                    "skipped",
                    mapOf("skip.reason" to "source_not_found"),
                ),
            )
            return
        }
        val tags = extractor.extract(
            PlaceTagExtractor.Request(
                place = event.place,
                body = source.body,
                hashtags = source.hashtags,
                imageUrls = source.imageUrls,
            ),
        ).distinctBy(InferredPlaceTag::tag).take(MAX_TAG_COUNT)
        updatePort.replace(event.postId, event.placeId, tags)
        logger.info(
            logEvent(
                event,
                "place.tags.completed",
                "complete",
                "success",
                mapOf("place.tag_count" to tags.size),
            ),
        )
    }

    private fun logEvent(
        event: PlaceTagsRequestedEvent,
        action: String,
        stage: String,
        outcome: String,
        fields: Map<String, Any?>,
    ) = ProcessingLogEvent(
        action = action,
        flow = TAG_FLOW,
        stage = stage,
        outcome = outcome,
        sourcePostId = event.postId,
        fields = fields + mapOf("place.id" to event.placeId),
    )

    private companion object {
        val logger = LoggerFactory.getLogger(StorePlaceTagsUseCase::class.java)
        const val TAG_FLOW = "place-tags"
        const val MAX_TAG_COUNT = 4
    }
}
