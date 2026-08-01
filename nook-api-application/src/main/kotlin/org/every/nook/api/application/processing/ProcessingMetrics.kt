package org.every.nook.api.application.processing

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
    record(
        ProcessingMetrics.Measurement(
            flow = flow,
            stage = stage,
            postId = postId,
            attempt = attempt,
            outcome = if (result.isSuccess) ProcessingMetrics.Outcome.SUCCESS else ProcessingMetrics.Outcome.FAILURE,
            duration = Duration.between(startedAt, clock.instant()),
        ),
    )
    return result.getOrThrow()
}
