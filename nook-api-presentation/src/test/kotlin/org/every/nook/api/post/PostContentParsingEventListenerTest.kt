package org.every.nook.api.post

import org.every.nook.api.application.post.FindOutstandingPostContentParsingJobsUseCase
import org.every.nook.api.application.post.OutstandingPostContentParsingJob
import org.every.nook.api.application.post.PostContentParsingJobRequestedEvent
import org.every.nook.api.application.post.ProcessPostContentParsingJobUseCase
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class PostContentParsingEventListenerTest {
    private val processUseCase = mock(ProcessPostContentParsingJobUseCase::class.java)
    private val findOutstandingUseCase = mock(FindOutstandingPostContentParsingJobsUseCase::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val listener = PostContentParsingEventListener(
        processPostContentParsingJob = processUseCase,
        findOutstandingJobs = findOutstandingUseCase,
        eventPublisher = eventPublisher,
        clock = CLOCK,
    )

    @Test
    fun `processes a requested content job`() {
        `when`(processUseCase(11)).thenReturn(ProcessPostContentParsingJobUseCase.Result.Completed)

        listener.process(PostContentParsingJobRequestedEvent(postId = 11))

        verify(processUseCase).invoke(11)
    }

    @Test
    fun `publishes recovery events for outstanding content jobs when the API becomes ready`() {
        `when`(findOutstandingUseCase()).thenReturn(
            listOf(OutstandingPostContentParsingJob(11, NOW.plus(PROCESSING_TIMEOUT))),
        )

        listener.recoverOutstandingJobs()

        val captor = ArgumentCaptor.forClass(PostContentParsingJobRequestedEvent::class.java)
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(11, captor.value.postId)
        assertEquals(NOW.plus(PROCESSING_TIMEOUT), captor.value.availableAt)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-29T00:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val PROCESSING_TIMEOUT: Duration = Duration.ofMinutes(15)
    }
}
