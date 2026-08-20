package org.every.nook.api.application.place

import java.time.Duration
import java.time.Instant

interface PlaceParsingJobPort {
    fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob?

    fun findOutstanding(processingTimeout: Duration): List<OutstandingPlaceParsingJob>

    fun storeImageTranscripts(postId: Long, transcripts: List<ImageTranscript>)

    fun complete(postId: Long, places: List<PlaceCandidate>, diagnostics: PlaceParsingDiagnostics)

    fun retry(postId: Long, nextAttemptAt: Instant, reason: String)

    fun fail(postId: Long, reason: String)
}

data class PlaceParsingDiagnostics(
    val outcome: Outcome,
    val expectedPlaceCount: Int?,
    val extractedPlaceCount: Int,
    val resolvedPlaceCount: Int,
    val unresolvedClues: List<UnresolvedPlaceClue>,
) {
    enum class Outcome { COMPLETED, PARTIAL }
}

data class UnresolvedPlaceClue(val clue: PlaceClue, val reason: String)

fun interface PlaceImageUrlPort {
    fun findImageUrls(postId: Long): List<String>
}

data class OutstandingPlaceParsingJob(val postId: Long, val availableAt: Instant)

data class PlaceCandidateWithThumbnail(val place: PlaceCandidate, val thumbnailUrl: String?)
