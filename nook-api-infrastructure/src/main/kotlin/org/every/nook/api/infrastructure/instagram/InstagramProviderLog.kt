package org.every.nook.api.infrastructure.instagram

import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.warn
import org.slf4j.Logger

internal fun instagramProviderEvent(
    provider: String,
    action: String,
    outcome: String,
    startedAt: Long? = null,
    fields: Map<String, Any?> = emptyMap(),
) = ProcessingLogEvent(
    action = action,
    flow = "post-content",
    stage = "extract",
    outcome = outcome,
    durationMs = startedAt?.let { (System.nanoTime() - it) / NANOS_PER_MILLISECOND },
    fields = fields + mapOf("provider.name" to provider.lowercase()),
)

private const val NANOS_PER_MILLISECOND = 1_000_000

internal fun Logger.logCacheHit(provider: String, startedAt: Long) = info(
    instagramProviderEvent(
        provider,
        "instagram.provider.cache.hit",
        "success",
        startedAt,
        mapOf("cache.hit" to true),
    ),
)

internal fun Logger.logProviderRequestStarted(provider: String) = info(
    instagramProviderEvent(
        provider,
        "instagram.provider.request.started",
        "started",
        fields = mapOf("cache.hit" to false),
    ),
)

internal fun Logger.logProviderRequestFailed(
    provider: String,
    startedAt: Long,
    exception: Throwable,
    statusCode: Int? = null,
) = warn(
    instagramProviderEvent(
        provider,
        "instagram.provider.request.failed",
        "failure",
        startedAt,
        mapOf("http.status_code" to statusCode),
    ),
    exception,
)

internal fun Logger.logProviderRequestCompleted(provider: String, startedAt: Long, mediaCount: Int) = info(
    instagramProviderEvent(
        provider,
        "instagram.provider.request.completed",
        "success",
        startedAt,
        mapOf("content.media_count" to mediaCount),
    ),
)
