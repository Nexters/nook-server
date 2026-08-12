package org.every.nook.api.application.processing

import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessingLogTest {
    @Test
    fun `processing context returns action result when logging backend has no MDC adapter`() {
        assertEquals(7, withProcessingLogContext(42, "place") { 7 })
    }

    @Test
    fun `structured event can omit nullable fields`() {
        LoggerFactory.getLogger(ProcessingLogTest::class.java).info(
            ProcessingLogEvent(
                action = "place.job.started",
                flow = "place",
                stage = "job",
                fields = mapOf("nullable" to null),
            ),
        )
    }
}
