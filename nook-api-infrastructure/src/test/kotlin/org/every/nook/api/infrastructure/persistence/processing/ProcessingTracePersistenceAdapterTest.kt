package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.processing.ProcessingTraceEvent
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessingTracePersistenceAdapterTest {
    private val repository = mock(ProcessingTraceJpaRepository::class.java)
    private val adapter = ProcessingTracePersistenceAdapter(repository, jacksonObjectMapper())

    @Test
    fun `stores sanitized structured parsing details`() {
        adapter.record(
            ProcessingTraceEvent(
                postId = 475,
                flow = "place",
                stage = "match",
                action = "place.candidates.matched",
                outcome = "failure",
                attempt = 1,
                durationMs = 12,
                details = mapOf("candidateCount" to "3", "addressCompatibleCount" to "0"),
            ),
        )

        val captor = ArgumentCaptor.forClass(ProcessingTraceEntity::class.java)
        verify(repository).save(captor.capture())
        assertEquals(475, captor.value.postId)
        assertEquals("failure", captor.value.outcome)
        assertEquals("{\"candidateCount\":\"3\",\"addressCompatibleCount\":\"0\"}", captor.value.details)
    }
}
