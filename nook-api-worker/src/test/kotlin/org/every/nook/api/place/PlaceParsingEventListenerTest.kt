package org.every.nook.api.place

import org.every.nook.api.application.place.ClaimedPlaceParsingJob
import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.place.OutstandingPlaceParsingJob
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceCandidateSelector
import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.place.PlaceParsingDiagnostics
import org.every.nook.api.application.place.PlaceParsingJobPort
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.application.place.ProcessPlaceParsingJobUseCase
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.every.nook.api.application.place.StorePlaceThumbnailUseCase
import org.every.nook.api.application.push.PushMessage
import org.every.nook.api.application.push.PushNotificationSender
import org.every.nook.api.application.push.PushPlatform
import org.every.nook.api.application.push.PushSendResult
import org.every.nook.api.application.push.PushToken
import org.every.nook.api.application.push.PushTokenPort
import org.every.nook.api.application.push.SendPostProcessingPushUseCase
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.TaskScheduler
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaceParsingEventListenerTest {
    private val jobPort = FakeJobPort()
    private val storePlaceThumbnail = mock(StorePlaceThumbnailUseCase::class.java)
    private val storePlaceTags = mock(StorePlaceTagsUseCase::class.java)
    private val pushSender = RecordingPushNotificationSender()
    private val sendPostProcessingPush = SendPostProcessingPushUseCase(FakePushTokenPort(), pushSender)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val retryTaskScheduler = mock(TaskScheduler::class.java)

    private val processPlaceParsingJob = ProcessPlaceParsingJobUseCase(
        jobPort = jobPort,
        imageTextExtractor = org.every.nook.api.application.place.ImageTextExtractor { emptyList() },
        clueExtractor = PlaceClueExtractor {
            listOf(org.every.nook.api.application.place.PlaceClue("롯지190", null, listOf("롯지190")))
        },
        searchPlaceCandidates = SearchPlaceCandidatesUseCase {
            listOf(
                PlaceCandidate(
                    provider = "KAKAO",
                    externalPlaceId = "1",
                    name = "롯지190",
                    address = "서울 서대문구",
                    latitude = BigDecimal("37.0"),
                    longitude = BigDecimal("127.0"),
                    category = null,
                    phoneNumber = null,
                    providerUrl = null,
                ),
            )
        },
        candidateSelector = PlaceCandidateSelector { null },
        retryBackoffs = listOf(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)),
        processingTimeout = PROCESSING_TIMEOUT,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )
    private val listener = listener(processPlaceParsingJob)

    private fun listener(processPlaceParsingJob: ProcessPlaceParsingJobUseCase) = PlaceParsingEventListener(
        processPlaceParsingJob = processPlaceParsingJob,
        findOutstandingJobs = FindOutstandingPlaceParsingJobsUseCase(jobPort, PROCESSING_TIMEOUT),
        storePlaceThumbnail = storePlaceThumbnail,
        storePlaceTags = storePlaceTags,
        sendPostProcessingPush = sendPostProcessingPush,
        eventPublisher = eventPublisher,
        retryTaskScheduler = retryTaskScheduler,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private fun failingProcessPlaceParsingJob() = ProcessPlaceParsingJobUseCase(
        jobPort = jobPort,
        imageTextExtractor = org.every.nook.api.application.place.ImageTextExtractor { emptyList() },
        clueExtractor = PlaceClueExtractor { error("provider failure") },
        searchPlaceCandidates = SearchPlaceCandidatesUseCase { emptyList() },
        candidateSelector = PlaceCandidateSelector { null },
        retryBackoffs = listOf(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3)),
        processingTimeout = PROCESSING_TIMEOUT,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `processes a requested job`() {
        listener.process(PlaceParsingJobRequestedEvent(postId = 11))

        assertTrue(jobPort.completed)
    }

    @Test
    fun `publishes recovery events for outstanding jobs when the API becomes ready`() {
        jobPort.outstanding = listOf(OutstandingPlaceParsingJob(11, NOW.plus(PROCESSING_TIMEOUT)))

        listener.recoverOutstandingJobs()

        val captor = ArgumentCaptor.forClass(PlaceParsingJobRequestedEvent::class.java)
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(11, captor.value.postId)
        assertEquals(NOW.plus(PROCESSING_TIMEOUT), captor.value.availableAt)
    }

    @Test
    fun `schedules a future job without waiting on the parsing worker`() {
        listener.process(PlaceParsingJobRequestedEvent(postId = 11, availableAt = NOW.plusSeconds(3)))

        val instantCaptor = ArgumentCaptor.forClass(Instant::class.java)
        verify(retryTaskScheduler).schedule(
            org.mockito.ArgumentMatchers.any(Runnable::class.java),
            instantCaptor.capture(),
        )
        assertEquals(NOW.plusSeconds(3), instantCaptor.value)
    }

    @Test
    fun `sends a completed push when only place parsing fails`() {
        jobPort.attempt = 4
        val failedJobListener = listener(failingProcessPlaceParsingJob())

        failedJobListener.process(PlaceParsingJobRequestedEvent(postId = 11))

        assertEquals("11", pushSender.message?.data?.get("postId"))
        assertEquals("COMPLETED", pushSender.message?.data?.get("outcome"))
    }

    private class FakePushTokenPort : PushTokenPort {
        override fun register(userId: Long, token: String, platform: PushPlatform) = Unit

        override fun delete(userId: Long, token: String) = Unit

        override fun findEnabledTokensByPostId(postId: Long): List<PushToken> =
            listOf(PushToken("token-1", PushPlatform.IOS))

        override fun disable(tokens: Collection<String>, reason: String) = Unit
    }

    private class RecordingPushNotificationSender : PushNotificationSender {
        var message: PushMessage? = null
            private set

        override fun send(tokens: List<String>, message: PushMessage): PushSendResult {
            this.message = message
            return PushSendResult(successCount = tokens.size, failureCount = 0, invalidTokens = emptyList())
        }
    }

    private class FakeJobPort : PlaceParsingJobPort {
        var completed = false
        var outstanding = emptyList<OutstandingPlaceParsingJob>()
        var attempt = 1

        override fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob =
            ClaimedPlaceParsingJob(postId, attempt, "롯지190", emptyList(), null)

        override fun findOutstanding(processingTimeout: Duration): List<OutstandingPlaceParsingJob> = outstanding

        override fun updateProgress(
            postId: Long,
            attempt: Int,
            stage: org.every.nook.api.application.processing.ParsingProgressStage,
        ) = true

        override fun storeImageTranscripts(
            postId: Long,
            attempt: Int,
            transcripts: List<org.every.nook.api.application.place.ImageTranscript>,
        ) = true

        override fun complete(
            postId: Long,
            attempt: Int,
            title: String?,
            places: List<PlaceCandidate>,
            diagnostics: PlaceParsingDiagnostics,
        ): Boolean {
            completed = true
            return true
        }

        override fun retry(postId: Long, attempt: Int, nextAttemptAt: Instant, reason: String) = true

        override fun fail(postId: Long, attempt: Int, title: String, reason: String) = true
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-28T00:00:00Z")
        val PROCESSING_TIMEOUT: Duration = Duration.ofMinutes(1)
    }
}
