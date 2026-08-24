package org.every.nook.api.application.post

import org.every.nook.api.application.content.SourceProfileHint
import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.application.processing.ParsingProgressStage
import org.every.nook.api.domain.post.Post
import java.time.Duration
import java.time.Instant

interface PostContentParsingJobPort {
    fun claim(postId: Long, processingTimeout: Duration): ClaimedPostContentParsingJob?

    fun findOutstanding(processingTimeout: Duration): List<OutstandingPostContentParsingJob>

    fun findOutstanding(processingTimeout: Duration, limit: Int): List<OutstandingPostContentParsingJob> =
        findOutstanding(processingTimeout).take(limit)

    fun updateProgress(postId: Long, attempt: Int, stage: ParsingProgressStage): Boolean

    fun complete(
        postId: Long,
        attempt: Int,
        post: Post,
        textPlaceClues: List<PlaceClue>,
        imageTranscripts: List<ImageTranscript> = emptyList(),
        sourceProfileHints: List<SourceProfileHint> = emptyList(),
    ): Boolean

    fun retry(postId: Long, attempt: Int, nextAttemptAt: Instant, reason: String): Boolean

    fun fail(postId: Long, attempt: Int, reason: String): Boolean
}

data class ClaimedPostContentParsingJob(val postId: Long, val attempt: Int, val canonicalUrl: String)

data class OutstandingPostContentParsingJob(val postId: Long, val availableAt: Instant)
