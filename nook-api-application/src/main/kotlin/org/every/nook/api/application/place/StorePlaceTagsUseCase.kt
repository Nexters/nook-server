package org.every.nook.api.application.place

class StorePlaceTagsUseCase(
    private val sourcePort: PlaceTagSourcePort,
    private val extractor: PlaceTagExtractor,
    private val updatePort: PlaceTagUpdatePort,
) {
    operator fun invoke(event: PlaceTagsRequestedEvent) {
        val source = sourcePort.find(event.postId) ?: return
        val tags = extractor.extract(
            PlaceTagExtractor.Request(
                place = event.place,
                body = source.body,
                hashtags = source.hashtags,
                imageUrls = source.imageUrls,
            ),
        ).distinctBy(InferredPlaceTag::tag).take(MAX_TAG_COUNT)
        updatePort.replace(event.postId, event.placeId, tags)
    }

    private companion object {
        const val MAX_TAG_COUNT = 4
    }
}
