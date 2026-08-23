package org.every.nook.api.processing

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import mu.KotlinLogging
import org.every.nook.api.application.processing.ParsingQueueMetricsPort
import org.every.nook.api.application.processing.ProcessingTimeouts
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@Component
class ParsingQueueMetricsCollector(
    meterRegistry: MeterRegistry,
    private val metricsPort: ParsingQueueMetricsPort,
    @Value("\${post-content-parsing.worker.processing-timeout:15m}") contentProcessingTimeout: Duration,
    @Value("\${place-parsing.worker.processing-timeout:10m}") placeProcessingTimeout: Duration,
    @Value("\${parsing.follow-up.processing-timeout:5m}") followUpProcessingTimeout: Duration,
    private val clock: Clock,
) {
    private val processingTimeouts = ProcessingTimeouts(
        content = contentProcessingTimeout,
        place = placeProcessingTimeout,
        followUp = followUpProcessingTimeout,
    )
    private val gauges = QUEUES.associateWith { queue -> QueueGauges(meterRegistry, queue) }

    @Scheduled(fixedDelayString = "\${parsing.metrics.collector-interval:30s}")
    fun collect() {
        val now = clock.instant()
        runCatching { metricsPort.observe(now, processingTimeouts) }
            .onSuccess { observations ->
                observations.forEach { observation ->
                    gauges[observation.queue]?.update(
                        readyJobs = observation.readyJobs,
                        oldestReadyAgeSeconds = observation.oldestReadyAt
                            ?.let { Duration.between(it, now).seconds.coerceAtLeast(0) }
                            ?: 0,
                        processingJobs = observation.processingJobs,
                        stuckProcessingJobs = observation.stuckProcessingJobs,
                        failedJobs = observation.failedJobs,
                    )
                }
            }
            .onFailure { exception -> logger.warn(exception) { "Failed to collect parsing queue metrics" } }
    }

    private class QueueGauges(meterRegistry: MeterRegistry, queue: String) {
        private val readyJobs = AtomicLong()
        private val oldestReadyAgeSeconds = AtomicLong()
        private val processingJobs = AtomicLong()
        private val stuckProcessingJobs = AtomicLong()
        private val failedJobs = AtomicLong()

        init {
            register(meterRegistry, "nook.parsing.queue.ready.jobs", queue, readyJobs)
            register(meterRegistry, "nook.parsing.queue.oldest.ready.age", queue, oldestReadyAgeSeconds)
            register(meterRegistry, "nook.parsing.queue.processing.jobs", queue, processingJobs)
            register(meterRegistry, "nook.parsing.queue.stuck.processing.jobs", queue, stuckProcessingJobs)
            register(meterRegistry, "nook.parsing.queue.failed.jobs", queue, failedJobs)
        }

        fun update(
            readyJobs: Long,
            oldestReadyAgeSeconds: Long,
            processingJobs: Long,
            stuckProcessingJobs: Long,
            failedJobs: Long,
        ) {
            this.readyJobs.set(readyJobs)
            this.oldestReadyAgeSeconds.set(oldestReadyAgeSeconds)
            this.processingJobs.set(processingJobs)
            this.stuckProcessingJobs.set(stuckProcessingJobs)
            this.failedJobs.set(failedJobs)
        }

        private fun register(meterRegistry: MeterRegistry, name: String, queue: String, value: AtomicLong) {
            Gauge.builder(name, value) { it.get().toDouble() }
                .tag("queue", queue)
                .register(meterRegistry)
        }
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        val QUEUES = setOf("content", "place", "follow_up")
    }
}
