package org.every.nook.api.processing

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.every.nook.api.application.processing.ParsingQueueMetricsPort
import org.every.nook.api.application.processing.ParsingQueueObservation
import org.every.nook.api.application.processing.ProcessingTimeouts
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ParsingQueueMetricsCollectorTest {
    private val now = Instant.parse("2026-08-24T00:00:00Z")
    private val registry = SimpleMeterRegistry()

    @Test
    fun `publishes queue counts and oldest ready age without a new metrics table`() {
        val port = RecordingPort(
            listOf(ParsingQueueObservation("content", 3, now.minusSeconds(75), 1, 1, 2)),
        )
        val collector = ParsingQueueMetricsCollector(
            meterRegistry = registry,
            metricsPort = port,
            contentProcessingTimeout = Duration.ofMinutes(15),
            placeProcessingTimeout = Duration.ofMinutes(10),
            followUpProcessingTimeout = Duration.ofMinutes(5),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )

        collector.collect()

        assertEquals(3.0, gauge("nook.parsing.queue.ready.jobs", "content"))
        assertEquals(75.0, gauge("nook.parsing.queue.oldest.ready.age.seconds", "content"))
        assertEquals(1.0, gauge("nook.parsing.queue.stuck.processing.jobs", "content"))
        assertEquals(Duration.ofMinutes(15), port.timeouts?.content)
    }

    private fun gauge(name: String, queue: String) = registry.get(name).tag("queue", queue).gauge().value()

    private class RecordingPort(private val observations: List<ParsingQueueObservation>) : ParsingQueueMetricsPort {
        var timeouts: ProcessingTimeouts? = null

        override fun observe(now: Instant, processingTimeouts: ProcessingTimeouts): List<ParsingQueueObservation> {
            timeouts = processingTimeouts
            return observations
        }
    }
}
