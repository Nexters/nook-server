package org.every.nook.api.application.place

internal fun isNormalEmptyPlaceResult(clueCount: Int, expectedCount: Int?, failure: Throwable?): Boolean =
    clueCount == 0 && (expectedCount ?: 0) == 0 && failure == null

internal data class ClueResolution(
    val places: List<PlaceCandidate>,
    val failure: PlaceResolutionException?,
    val clueCount: Int,
    val expectedPlaceCount: Int?,
    val unresolvedClues: List<UnresolvedPlaceClue>,
    val clues: List<PlaceClue>,
    val imageTranscripts: List<ImageTranscript> = emptyList(),
)

internal class TerminalPlaceParsingException(message: String, val title: String) : IllegalStateException(message)

internal fun unresolvedPlaceReason(text: ClueResolution, image: ClueResolution?): String =
    (text.failure ?: image?.failure)?.message ?: if (image == null) {
        "No place could be resolved from text"
    } else {
        "No place could be resolved after image analysis"
    }
