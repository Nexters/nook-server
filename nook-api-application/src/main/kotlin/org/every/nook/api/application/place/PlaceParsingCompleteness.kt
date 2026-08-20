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

internal fun List<ImageTranscript>.detectedPlaceCardCount(): Int = mapNotNull { transcript ->
    transcript.texts.asSequence()
        .flatMap { text -> ADDRESS_PATTERN.findAll(text) }
        .map { match -> match.value.placeCardAddressKey() }
        .firstOrNull()
}.distinct().count()

internal fun effectiveExpectedPlaceCount(textExpectedPlaceCount: Int?, transcripts: List<ImageTranscript>): Int? =
    listOfNotNull(textExpectedPlaceCount, transcripts.detectedPlaceCardCount()).maxOrNull()

internal fun PlaceClue.restoreShortPlaceName(transcripts: List<ImageTranscript>): PlaceClue {
    if (addressHint.isNullOrBlank()) return this
    val evidenceIndexes = evidence.map(PlaceClueEvidence::imageIndex).toSet()
    val shortName = transcripts.asSequence()
        .filter { transcript -> transcript.imageIndex in evidenceIndexes }
        .flatMap(ImageTranscript::texts)
        .mapNotNull { text ->
            val addressStart = ADDRESS_PATTERN.find(text)?.range?.first ?: return@mapNotNull null
            text.substring(0, addressStart).trim().split(Regex("\\s+")).lastOrNull()
        }
        .firstOrNull { candidate -> candidate.length == 1 && !name.contains(candidate) }
        ?: return this
    return copy(name = shortName, queries = listOf(shortName) + queries)
}

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

internal fun PlaceClue.hasExclusiveGroundedImageEvidence(clues: List<PlaceClue>): Boolean {
    val imageIndexes = evidence.map(PlaceClueEvidence::imageIndex).distinct()
    if (imageIndexes.size != 1) return false
    val imageIndex = imageIndexes.single()
    if (clues.count { clue -> clue.evidence.any { it.imageIndex == imageIndex } } != 1) return false
    val groundingKeys = listOfNotNull(name, addressHint)
        .map { it.lowercase().filter(Char::isLetterOrDigit) }
        .filter { it.length >= MIN_PLACE_NAME_TEXT_LENGTH }
    return evidence
        .filter { it.imageIndex == imageIndex }
        .map { it.evidenceText.lowercase().filter(Char::isLetterOrDigit) }
        .any { evidenceText -> groundingKeys.any(evidenceText::contains) }
}

private val ADDRESS_PATTERN = Regex(
    "(?:[가-힣]+(?:시|도)\\s+)?[가-힣]+(?:구|군|시)\\s+[가-힣A-Za-z0-9·.-]+(?:로|길|동)\\s*\\d+",
)

private fun String.placeCardAddressKey(): String = lowercase().filter(Char::isLetterOrDigit)
private const val MIN_PLACE_NAME_TEXT_LENGTH = 2
private val completenessLogger = KotlinLogging.logger {}
