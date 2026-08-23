package org.every.nook.api.application.processing

import java.time.Duration
import java.time.Instant

interface ParsingQueueMetricsPort {
    fun observe(now: Instant, processingTimeouts: ProcessingTimeouts): List<ParsingQueueObservation>
}

data class ProcessingTimeouts(val content: Duration, val place: Duration, val followUp: Duration)

data class ParsingQueueObservation(
    val queue: String,
    val readyJobs: Long,
    val oldestReadyAt: Instant?,
    val processingJobs: Long,
    val stuckProcessingJobs: Long,
    val failedJobs: Long,
)
