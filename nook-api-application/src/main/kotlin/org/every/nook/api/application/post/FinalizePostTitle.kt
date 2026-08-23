package org.every.nook.api.application.post

import mu.KotlinLogging
import org.every.nook.api.application.place.ClaimedPlaceParsingJob
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.processing.ParsingRuleEvaluation

internal class FinalizePostTitle(private val selector: PostTitleSelector) {
    operator fun invoke(
        job: ClaimedPlaceParsingJob,
        places: List<PlaceCandidate>,
        declaredPlaceCount: Int?,
        latestImageTranscripts: List<ImageTranscript>,
        onEvaluation: (ParsingRuleEvaluation) -> Unit = {},
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
        val selected = runCatching {
            selector.select(request)
        }.onFailure { exception ->
            logger.warn(exception) { "Post title selection failed; using fallback: postId=${job.postId}" }
        }.getOrNull().also { result ->
            logger.info {
                "Post title selected: postId=${job.postId}, source=${result?.source}, " +
                    "rejectedCoverReason=${result?.rejectedCoverReason}"
            }
        }
        return PostTitleFinalizationPolicy().evaluate(PostTitleFinalizationPolicy.Context(request, selected))
            .also { evaluation -> evaluation.ruleEvaluations.forEach(onEvaluation) }
            .result
    }

    private companion object {
        val logger = KotlinLogging.logger {}
    }
}

internal const val DEFAULT_POST_TITLE = "방문해보기 좋은 곳"
