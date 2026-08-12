package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.warn
import org.slf4j.Logger

internal fun PlaceCandidate.event(
    action: String,
    stage: String,
    outcome: String,
    fields: Map<String, Any?> = emptyMap(),
) = ProcessingLogEvent(
    action = action,
    flow = "place-thumbnail",
    stage = stage,
    outcome = outcome,
    fields = fields + mapOf(
        "provider.name" to "google",
        "place.source_provider" to provider,
        "place.external_id" to externalPlaceId,
    ),
)

internal fun PlaceCandidate.photoEvent(
    action: String,
    stage: String,
    sequence: Int,
    exception: Throwable,
    startedAt: Long? = null,
) = event(
    action,
    stage,
    "failure",
    mapOf(
        "event.duration_ms" to startedAt?.let(::elapsedMillis),
        "media.sequence" to sequence,
        "failure.type" to exception::class.simpleName,
        "failure.reason" to exception.message?.take(MAX_FAILURE_REASON_LENGTH),
    ),
)

internal fun failureFields(exception: Throwable): Map<String, Any?> = mapOf(
    "failure.type" to exception::class.simpleName,
    "failure.reason" to exception.message?.take(MAX_FAILURE_REASON_LENGTH),
)

internal fun elapsedMillis(startedAt: Long): Long = (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND

internal fun Logger.logGoogleSkipped(place: PlaceCandidate) = warn(
    place.event(
        "google.place.skipped",
        "configuration",
        "skipped",
        mapOf("skip.reason" to "invalid_configuration"),
    ),
)

internal fun Logger.logGooglePhotoList(place: PlaceCandidate, availableCount: Int, selectedCount: Int) = info(
    place.event(
        "google.photo.list.completed",
        "google-photo-list",
        if (selectedCount == 0) "empty" else "success",
        mapOf(
            "google.photo_available_count" to availableCount,
            "google.photo_selected_count" to selectedCount,
            "empty.reason" to if (selectedCount == 0) "no_photos" else null,
        ),
    ),
)

internal fun Logger.logGooglePhotoPipeline(place: PlaceCandidate, selectedCount: Int, storedCount: Int) = info(
    place.event(
        "google.photo.pipeline.completed",
        "google-photo-store",
        if (storedCount == 0 && selectedCount > 0) "failure" else "success",
        mapOf(
            "google.photo_selected_count" to selectedCount,
            "google.photo_stored_count" to storedCount,
            "google.photo_failed_count" to selectedCount - storedCount,
        ),
    ),
)

internal fun Logger.logGoogleFetchFailure(place: PlaceCandidate, exception: Throwable, startedAt: Long) = warn(
    place.event(
        "google.place.fetch.failed",
        "google-place-match",
        "failure",
        failureFields(exception) + ("event.duration_ms" to elapsedMillis(startedAt)),
    ),
    exception,
)

private const val MAX_FAILURE_REASON_LENGTH = 500
private const val NANOS_PER_MILLISECOND = 1_000_000
