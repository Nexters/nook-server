package org.every.nook.api.application.post

import mu.KotlinLogging
import org.every.nook.api.application.place.ClaimedPlaceParsingJob
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.PlaceCandidate

internal class FinalizePostTitle(private val selector: PostTitleSelector) {
    operator fun invoke(
        job: ClaimedPlaceParsingJob,
        places: List<PlaceCandidate>,
        declaredPlaceCount: Int?,
        latestImageTranscripts: List<ImageTranscript>,
    ): String {
        val transcripts = latestImageTranscripts.ifEmpty { job.imageTranscripts.orEmpty() }
        val request = PostTitleSelector.Request(
            body = job.body,
            hashtags = job.hashtags,
            sourceLocationTag = job.sourceLocationTag,
            coverTexts = transcripts.firstOrNull { it.imageIndex == 1 }?.texts.orEmpty(),
            declaredPlaceCount = declaredPlaceCount,
            places = places.map { place ->
                PostTitleSelector.Place(place.name, place.address, place.city, place.category)
            },
        )
        val fallback = fallbackPostTitle(request)
        return runCatching {
            selector.select(request)
        }.onFailure { exception ->
            logger.warn(exception) { "Post title selection failed; using fallback: postId=${job.postId}" }
        }.getOrNull()?.let { result ->
            val selected = result.title.validPostTitle()
                ?.take(MAX_FINAL_TITLE_LENGTH)
                ?.takeUnless { result.source == PostTitleSelector.Source.NONE }
                ?.takeIf { it.hasConsistentPlaceCount(request.declaredPlaceCount, request.places.size) }
            logger.info {
                "Post title selected: postId=${job.postId}, source=${result.source}, " +
                    "rejectedCoverReason=${result.rejectedCoverReason}, usedFallback=${selected == null}"
            }
            selected
        } ?: fallback ?: DEFAULT_POST_TITLE
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}

internal const val DEFAULT_POST_TITLE = "방문해보기 좋은 곳"
