package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ProcessingLogEvent

internal fun ClaimedPlaceParsingJob.event(
    action: String,
    stage: String,
    outcome: String,
    durationMs: Long? = null,
    fields: Map<String, Any?> = emptyMap(),
) = ProcessingLogEvent(action, PLACE_FLOW, stage, outcome, postId, attempt, durationMs, fields)

internal fun failureFields(exception: Throwable, reason: String): Map<String, Any?> = mapOf(
    "failure.type" to exception::class.simpleName,
    "failure.reason" to reason,
)

internal fun placeFailureReason(exception: Throwable): String = exception.message.orEmpty()
    .ifBlank { DEFAULT_FAILURE_REASON }
    .take(MAX_FAILURE_REASON_LENGTH)

private const val PLACE_FLOW = "place"
private const val DEFAULT_FAILURE_REASON = "Place parsing failed"
private const val MAX_FAILURE_REASON_LENGTH = 500

// A successful fallback title must not hide the unresolved place stage in terminal alerts.
internal fun unresolvedPlaceStage(hasImageResolution: Boolean) = if (hasImageResolution) {
    org.every.nook.api.application.processing.ParsingProgressStage.PLACE_IMAGE_RESOLUTION
} else {
    org.every.nook.api.application.processing.ParsingProgressStage.PLACE_TEXT_RESOLUTION
}

internal fun failResolution(message: String): Nothing = throw PlaceResolutionException(message)

internal class PlaceResolutionException(message: String) : IllegalStateException(message)
