package org.every.nook.api.post

import org.every.nook.api.application.post.FindOutstandingPostContentParsingJobsUseCase
import org.every.nook.api.application.post.OutstandingPostContentParsingJob
import org.every.nook.api.application.post.PostContentParsingJobRequestedEvent
import org.every.nook.api.application.post.PostMediaStorageRequestedEvent
import org.every.nook.api.application.post.ProcessPostContentParsingJobUseCase
import org.every.nook.api.application.post.StorePostMediaUseCase
import org.every.nook.api.application.push.SendPostProcessingPushUseCase
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.TaskScheduler
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class PostContentParsingEventListenerTest {
    private val processUseCase = mock(ProcessPostContentParsingJobUseCase::class.java)
    private val findOutstandingUseCase = mock(FindOutstandingPostContentParsingJobsUseCase::class.java)
    private val storePostMedia = mock(StorePostMediaUseCase::class.java)
    private val sendPostProcessingPush = mock(SendPostProcessingPushUseCase::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val retryTaskScheduler = mock(TaskScheduler::class.java)
    private val listener = PostContentParsingEventListener(
        processPostContentParsingJob = processUseCase,
        findOutstandingJobs = findOutstandingUseCase,
        storePostMedia = storePostMedia,
        sendPostProcessingPush = sendPostProcessingPush,
        eventPublisher = eventPublisher,
        retryTaskScheduler = retryTaskScheduler,
        clock = CLOCK,
    )

    @Test
    fun `processes a requested content job`() {
        `when`(processUseCase(11)).thenReturn(ProcessPostContentParsingJobUseCase.Result.Completed)

        listener.process(PostContentParsingJobRequestedEvent(postId = 11))

        verify(processUseCase).invoke(11)
    }

    @Test
    fun `sends a failed push when content parsing fails permanently`() {
        `when`(processUseCase(11)).thenReturn(ProcessPostContentParsingJobUseCase.Result.Failed)

        listener.process(PostContentParsingJobRequestedEvent(postId = 11))

        verify(sendPostProcessingPush).invoke(
            SendPostProcessingPushUseCase.Command(
                postId = 11,
                outcome = SendPostProcessingPushUseCase.Outcome.FAILED,
            ),
        )
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

    @Test
    fun `schedules a retry without waiting on the parsing worker`() {
        `when`(processUseCase(11)).thenReturn(
            ProcessPostContentParsingJobUseCase.Result.Retry(NOW.plusSeconds(3)),
        )

        listener.process(PostContentParsingJobRequestedEvent(postId = 11))

        val instantCaptor = ArgumentCaptor.forClass(Instant::class.java)
        verify(retryTaskScheduler).schedule(
            org.mockito.ArgumentMatchers.any(Runnable::class.java),
            instantCaptor.capture(),
        )
        assertEquals(NOW.plusSeconds(3), instantCaptor.value)
    }

    @Test
    fun `schedules media storage retry without blocking its worker`() {
        val event = PostMediaStorageRequestedEvent(
            postId = 11,
            mediaType = "IMAGE",
            sourceUrl = "https://source.example.com/image.jpg",
            sequence = 0,
        )
        doThrow(IllegalStateException("temporary storage failure"))
            .`when`(storePostMedia)
            .invoke(
                11,
                StorePostMediaUseCase.Command("IMAGE", "https://source.example.com/image.jpg", 0),
            )

        listener.storeMedia(event)

        val instantCaptor = ArgumentCaptor.forClass(Instant::class.java)
        verify(retryTaskScheduler).schedule(
            org.mockito.ArgumentMatchers.any(Runnable::class.java),
            instantCaptor.capture(),
        )
        assertEquals(NOW.plusSeconds(3), instantCaptor.value)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-29T00:00:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val PROCESSING_TIMEOUT: Duration = Duration.ofMinutes(15)
    }
}
