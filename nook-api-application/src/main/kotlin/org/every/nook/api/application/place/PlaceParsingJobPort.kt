package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ParsingProgressStage
import java.time.Duration
import java.time.Instant

interface PlaceParsingJobPort {
    fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob?

    fun findOutstanding(processingTimeout: Duration): List<OutstandingPlaceParsingJob>

    fun findOutstanding(processingTimeout: Duration, limit: Int): List<OutstandingPlaceParsingJob> =
        findOutstanding(processingTimeout).take(limit)

    fun updateProgress(postId: Long, attempt: Int, stage: ParsingProgressStage): Boolean

    fun storeImageTranscripts(postId: Long, attempt: Int, transcripts: List<ImageTranscript>): Boolean

    fun complete(
        postId: Long,
        attempt: Int,
        title: String?,
        places: List<PlaceCandidate>,
        diagnostics: PlaceParsingDiagnostics,
    ): Boolean

    fun retry(postId: Long, attempt: Int, nextAttemptAt: Instant, reason: String): Boolean

    fun fail(postId: Long, attempt: Int, title: String, reason: String): Boolean
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

data class UnresolvedPlaceClue(val clue: PlaceClue, val reason: String, val type: Type = Type.RESOLUTION_FAILED) {
    enum class Type {
        NOT_EXTRACTED,
        RESOLUTION_FAILED,
    }
}

fun interface PlaceImageUrlPort {
    fun findImageUrls(postId: Long): List<String>
}

data class OutstandingPlaceParsingJob(val postId: Long, val availableAt: Instant)

data class PlaceCandidateWithThumbnail(val place: PlaceCandidate, val thumbnailUrl: String?)
