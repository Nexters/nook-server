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

internal fun List<ImageTranscript>.detectedPlaceCardCount(): Int {
    val addressCardCount = mapNotNull { transcript ->
        transcript.texts.asSequence()
            .flatMap { text -> ADDRESS_PATTERN.findAll(text) }
            .map { match -> match.value.placeCardAddressKey() }
            .firstOrNull()
    }.distinct().count()
    val declaredPickCount = asSequence()
        .flatMap { transcript -> transcript.texts.asSequence() }
        .flatMap { text -> PICK_COUNT_PATTERN.findAll(text) }
        .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
        .filter { count -> count in MIN_PICK_COUNT..MAX_PICK_COUNT }
        .maxOrNull()
    return maxOf(addressCardCount, declaredPickCount ?: 0)
}

internal fun effectiveExpectedPlaceCount(textExpectedPlaceCount: Int?, transcripts: List<ImageTranscript>): Int? =
    listOfNotNull(textExpectedPlaceCount, transcripts.detectedPlaceCardCount()).maxOrNull()

internal fun PlaceClue.restoreGroundingFromCard(transcripts: List<ImageTranscript>): PlaceClue =
    restoreEvidenceFromTranscript(transcripts).restoreAddressFromCard(transcripts)

private fun PlaceClue.restoreEvidenceFromTranscript(transcripts: List<ImageTranscript>): PlaceClue {
    val identityKey = name.placeCardNameKey().takeIf { it.length >= MIN_EVIDENCE_IDENTITY_LENGTH } ?: return this
    val matches = transcripts.flatMap { transcript ->
        transcript.texts
            .filter { text -> text.placeCardNameKey().contains(identityKey) }
            .map { text -> PlaceClueEvidence(transcript.imageIndex, text) }
    }
    val matchedImageIndexes = matches.map(PlaceClueEvidence::imageIndex).distinct()
    return if (matchedImageIndexes.size == 1) copy(evidence = listOf(matches.first())) else this
}

private fun PlaceClue.restoreAddressFromCard(transcripts: List<ImageTranscript>): PlaceClue {
    val evidenceIndexes = evidence.map(PlaceClueEvidence::imageIndex).toSet()
    val cardTexts = transcripts.asSequence()
        .filter { transcript -> transcript.imageIndex in evidenceIndexes }
        .flatMap(ImageTranscript::texts)
        .mapNotNull { text -> PLACE_CARD_ADDRESS_PATTERN.find(text)?.let { match -> text to match } }
        .toList()
    val distinctAddresses = cardTexts
        .map { (_, match) -> match.value.trim() }
        .distinctBy(String::placeCardAddressKey)
    if (distinctAddresses.size != 1) return this
    val cardAddress = distinctAddresses.single()
    val cardName = cardTexts.asSequence()
        .map { (text, match) ->
            text.substring(0, match.range.first)
                .trim()
                .split(Regex("\\s+"))
                .dropLastWhile { token -> token == region || token in METROPOLITAN_REGION_NAMES }
                .joinToString(" ")
        }
        .firstOrNull { candidate ->
            candidate.isNotEmpty() &&
                candidate.length <= MAX_PLACE_CARD_NAME_LENGTH &&
                candidate.split(Regex("\\s+")).size <= MAX_PLACE_CARD_NAME_WORDS &&
                !name.placeCardNameKey().contains(candidate.placeCardNameKey())
        }
        ?: name
    val restoredAddress = addressHint?.takeIf { hint ->
        PlaceAddressMatcher.addressKeys(hint).intersect(PlaceAddressMatcher.addressKeys(cardAddress)).isNotEmpty()
    } ?: cardAddress
    val restoredEvidence = evidence.map { clueEvidence ->
        val transcriptText = cardTexts.firstOrNull { (text, _) ->
            text.placeCardAddressKey().contains(cardAddress.placeCardAddressKey())
        }?.first
        if (clueEvidence.imageIndex in evidenceIndexes && transcriptText != null) {
            clueEvidence.copy(evidenceText = transcriptText)
        } else {
            clueEvidence
        }
    }
    return copy(
        name = cardName,
        addressHint = restoredAddress,
        queries = listOf(cardName, restoredAddress) + queries,
        evidence = restoredEvidence,
    )
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
private val PLACE_CARD_ADDRESS_PATTERN = Regex(
    ADDRESS_PATTERN.pattern +
        "(?:-\\d+)?(?:\\s+(?:지(?:하)?\\s*\\d+\\s*층|B\\s*-?\\s*\\d+|\\d+(?:\\s*,\\s*\\d+)?\\s*층|\\d+\\s*호))*",
    RegexOption.IGNORE_CASE,
)
private val PICK_COUNT_PATTERN = Regex("(?i)\\bPICK\\s*[:#-]?\\s*(\\d{1,2})\\b")

private fun String.placeCardAddressKey(): String = lowercase().filter(Char::isLetterOrDigit)
private fun String.placeCardNameKey(): String = lowercase().filter(Char::isLetterOrDigit)
private const val MIN_PLACE_NAME_TEXT_LENGTH = 2
private const val MIN_EVIDENCE_IDENTITY_LENGTH = 2
private const val MAX_PLACE_CARD_NAME_LENGTH = 30
private const val MAX_PLACE_CARD_NAME_WORDS = 3
private const val MIN_PICK_COUNT = 2
private const val MAX_PICK_COUNT = 60
private val METROPOLITAN_REGION_NAMES = setOf(
    "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "제주",
)
private val completenessLogger = KotlinLogging.logger {}
