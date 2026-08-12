package org.every.nook.api.application.processing

import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration

fun interface ProcessingMetrics {
    fun record(measurement: Measurement)

    data class Measurement(
        val flow: String,
        val stage: String,
        val postId: Long,
        val attempt: Int?,
        val outcome: Outcome,
        val duration: Duration,
    )

    enum class Outcome {
        SUCCESS,
        FAILURE,
    }
}

object NoOpProcessingMetrics : ProcessingMetrics {
    override fun record(measurement: ProcessingMetrics.Measurement) = Unit
}

inline fun <T> ProcessingMetrics.measure(
    flow: String,
    stage: String,
    postId: Long,
    attempt: Int?,
    clock: Clock,
    action: () -> T,
): T {
    val startedAt = clock.instant()
    val result = runCatching(action)
    val duration = Duration.between(startedAt, clock.instant())
    record(
        ProcessingMetrics.Measurement(
            flow = flow,
            stage = stage,
            postId = postId,
            attempt = attempt,
            outcome = if (result.isSuccess) ProcessingMetrics.Outcome.SUCCESS else ProcessingMetrics.Outcome.FAILURE,
            duration = duration,
        ),
    )
    val event = ProcessingLogEvent(
        action = "processing.stage.completed",
        flow = flow,
        stage = stage,
        outcome = if (result.isSuccess) "success" else "failure",
        sourcePostId = postId,
        attempt = attempt,
        durationMs = duration.toMillis(),
        fields = result.exceptionOrNull()?.let { exception ->
            mapOf(
                ProcessingLogFields.FAILURE_TYPE to exception::class.simpleName,
                ProcessingLogFields.FAILURE_REASON to exception.message?.take(MAX_FAILURE_REASON_LENGTH),
            )
        }.orEmpty(),
    )
    if (result.isSuccess && isHighVolumeStage(flow, stage)) {
        processingStageLogger.debug(event)
    } else if (result.isSuccess) {
        processingStageLogger.info(event)
    } else {
        processingStageLogger.warn(event, result.exceptionOrNull())
    }
    return result.getOrThrow()
}

@PublishedApi
internal val processingStageLogger = LoggerFactory.getLogger("processing.stage")

@PublishedApi
internal const val MAX_FAILURE_REASON_LENGTH = 500

@PublishedApi
internal fun isHighVolumeStage(flow: String, stage: String): Boolean =
    flow == "post-media" || (flow == "place" && stage == "search")
