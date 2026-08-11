package org.every.nook.api.application.post

import org.every.nook.api.application.place.PlaceClue
import org.every.nook.api.domain.post.Post
import java.time.Duration
import java.time.Instant

interface PostContentParsingJobPort {
    fun claim(postId: Long, processingTimeout: Duration): ClaimedPostContentParsingJob?

    fun findOutstanding(processingTimeout: Duration): List<OutstandingPostContentParsingJob>

    fun complete(postId: Long, post: Post, textPlaceClues: List<PlaceClue>)

    fun retry(postId: Long, nextAttemptAt: Instant, reason: String)

    fun fail(postId: Long, reason: String)
}

data class ClaimedPostContentParsingJob(val postId: Long, val attempt: Int, val canonicalUrl: String)

data class OutstandingPostContentParsingJob(val postId: Long, val availableAt: Instant)
