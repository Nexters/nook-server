package org.every.nook.api.application.place

import java.time.Duration
import java.time.Instant

interface PlaceParsingJobPort {
    fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob?

    fun findOutstanding(processingTimeout: Duration): List<OutstandingPlaceParsingJob>

    fun complete(postId: Long, places: List<PlaceCandidate>)

    fun retry(postId: Long, nextAttemptAt: Instant, reason: String)

    fun fail(postId: Long, reason: String)
}

data class OutstandingPlaceParsingJob(val postId: Long, val availableAt: Instant)

data class PlaceCandidateWithThumbnail(val place: PlaceCandidate, val thumbnailUrl: String?)
