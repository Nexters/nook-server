package org.every.nook.api.application.processing

import org.slf4j.Logger
import org.slf4j.MDC

object ProcessingLogFields {
    const val EVENT_ACTION = "event.action"
    const val EVENT_OUTCOME = "event.outcome"
    const val EVENT_DURATION_MS = "event.duration_ms"
    const val FLOW = "processing.flow"
    const val STAGE = "processing.stage"
    const val ATTEMPT = "processing.attempt"
    const val SOURCE_POST_ID = "source_post.id"
    const val SAVED_POST_ID = "saved_post.id"
    const val PROVIDER = "provider.name"
    const val FAILURE_TYPE = "failure.type"
    const val FAILURE_REASON = "failure.reason"
}

data class ProcessingLogEvent(
    val action: String,
    val flow: String,
    val stage: String,
    val outcome: String? = null,
    val sourcePostId: Long? = null,
    val attempt: Int? = null,
    val durationMs: Long? = null,
    val fields: Map<String, Any?> = emptyMap(),
)

fun Logger.info(event: ProcessingLogEvent) = trackerBuilder(event).log(trackerMessage(event.action))

fun Logger.debug(event: ProcessingLogEvent) = trackerBuilder(event).log(trackerMessage(event.action))

fun Logger.warn(event: ProcessingLogEvent, cause: Throwable? = null) =
    trackerBuilder(event).setCause(cause).log(trackerMessage(event.action))

fun Logger.error(event: ProcessingLogEvent, cause: Throwable? = null) =
    trackerBuilder(event).setCause(cause).log(trackerMessage(event.action))

private fun Logger.trackerBuilder(event: ProcessingLogEvent) = eventBuilder(event, atDebug())

private fun trackerMessage(action: String) = "$TRACKER_PREFIX $action"

fun <T> withProcessingLogContext(sourcePostId: Long, flow: String, action: () -> T): T {
    val previous = MDC.getCopyOfContextMap()
    return try {
        MDC.put(ProcessingLogFields.SOURCE_POST_ID, sourcePostId.toString())
        MDC.put(ProcessingLogFields.FLOW, flow)
        action()
    } finally {
        if (previous == null) MDC.clear() else MDC.setContextMap(previous)
    }
}

private fun eventBuilder(event: ProcessingLogEvent, builder: org.slf4j.spi.LoggingEventBuilder) = builder
    .addKeyValue(ProcessingLogFields.EVENT_ACTION, event.action)
    .addKeyValue(ProcessingLogFields.FLOW, event.flow)
    .addKeyValue(ProcessingLogFields.STAGE, event.stage)
    .apply {
        event.outcome?.let { addKeyValue(ProcessingLogFields.EVENT_OUTCOME, it) }
        event.sourcePostId?.let { addKeyValue(ProcessingLogFields.SOURCE_POST_ID, it) }
        event.attempt?.let { addKeyValue(ProcessingLogFields.ATTEMPT, it) }
        event.durationMs?.let { addKeyValue(ProcessingLogFields.EVENT_DURATION_MS, it) }
        event.fields.filterValues { it != null }.forEach { (key, value) -> addKeyValue(key, value) }
    }

private const val TRACKER_PREFIX = "[PostParcingTracker]"
