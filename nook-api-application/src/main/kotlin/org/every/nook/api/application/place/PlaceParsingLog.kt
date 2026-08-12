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
