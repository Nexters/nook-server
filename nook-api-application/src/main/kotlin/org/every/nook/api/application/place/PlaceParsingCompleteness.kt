package org.every.nook.api.application.place

import mu.KotlinLogging
import org.every.nook.api.application.processing.warn

internal fun placeParsingDiagnostics(
    textExpectedPlaceCount: Int?,
    imageExpectedPlaceCount: Int?,
    extractedPlaceCount: Int,
    resolvedPlaceCount: Int,
    unresolvedClues: List<UnresolvedPlaceClue>,
): PlaceParsingDiagnostics {
    val expectedPlaceCount = listOfNotNull(textExpectedPlaceCount, imageExpectedPlaceCount).maxOrNull()
    val outcome = if (
        unresolvedClues.isNotEmpty() || expectedPlaceCount?.let { resolvedPlaceCount < it } == true
    ) {
        PlaceParsingDiagnostics.Outcome.PARTIAL
    } else {
        PlaceParsingDiagnostics.Outcome.COMPLETED
    }
    return PlaceParsingDiagnostics(
        outcome = outcome,
        expectedPlaceCount = expectedPlaceCount,
        extractedPlaceCount = extractedPlaceCount,
        resolvedPlaceCount = resolvedPlaceCount,
        unresolvedClues = unresolvedClues,
    )
}

internal fun List<ImageTranscript>.detectedPlaceCardCount(): Int = count { transcript ->
    val hasAddress = transcript.texts.any { ADDRESS_PATTERN.containsMatchIn(it) }
    val hasPlaceName = transcript.texts.any { text ->
        text.length >= MIN_PLACE_NAME_TEXT_LENGTH && !ADDRESS_PATTERN.containsMatchIn(text)
    }
    hasAddress && hasPlaceName
}

internal fun effectiveExpectedPlaceCount(textExpectedPlaceCount: Int?, transcripts: List<ImageTranscript>): Int? =
    listOfNotNull(textExpectedPlaceCount, transcripts.detectedPlaceCardCount()).maxOrNull()

internal fun List<PlaceClue>.filterGroundedTextClues(job: ClaimedPlaceParsingJob): List<PlaceClue> = filter { clue ->
    clue.isGroundedIn(job.body, job.hashtags).also { grounded ->
        if (!grounded) {
            completenessLogger.warn {
                "Ungrounded text place clue skipped: postId=${job.postId}, attempt=${job.attempt}, " +
                    "placeName=${clue.name}, region=${clue.region}, queries=${clue.queries}"
            }
        }
    }
}

private val ADDRESS_PATTERN = Regex(
    "(?:[가-힣]+(?:시|도)\\s+)?[가-힣]+(?:구|군|시)\\s+[가-힣A-Za-z0-9·.-]+(?:로|길|동)\\s*\\d+",
)
private const val MIN_PLACE_NAME_TEXT_LENGTH = 2
private val completenessLogger = KotlinLogging.logger {}
