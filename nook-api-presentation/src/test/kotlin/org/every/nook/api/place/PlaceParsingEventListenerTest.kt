package org.every.nook.api.place

import org.every.nook.api.application.place.ClaimedPlaceParsingJob
import org.every.nook.api.application.place.FindOutstandingPlaceParsingJobsUseCase
import org.every.nook.api.application.place.OutstandingPlaceParsingJob
import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceCandidateSelector
import org.every.nook.api.application.place.PlaceCandidateWithThumbnail
import org.every.nook.api.application.place.PlaceClueExtractor
import org.every.nook.api.application.place.PlaceParsingJobPort
import org.every.nook.api.application.place.PlaceParsingJobRequestedEvent
import org.every.nook.api.application.place.ProcessPlaceParsingJobUseCase
import org.every.nook.api.application.place.SearchPlaceCandidatesUseCase
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.context.ApplicationEventPublisher
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
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)
    private val listener = PlaceParsingEventListener(
        processPlaceParsingJob = ProcessPlaceParsingJobUseCase(
            jobPort = jobPort,
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
        ),
        findOutstandingJobs = FindOutstandingPlaceParsingJobsUseCase(jobPort, PROCESSING_TIMEOUT),
        eventPublisher = eventPublisher,
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

    private class FakeJobPort : PlaceParsingJobPort {
        var completed = false
        var outstanding = emptyList<OutstandingPlaceParsingJob>()

        override fun claim(postId: Long, processingTimeout: Duration): ClaimedPlaceParsingJob =
            ClaimedPlaceParsingJob(postId, 1, null, emptyList(), null)

        override fun findOutstanding(processingTimeout: Duration): List<OutstandingPlaceParsingJob> = outstanding

        override fun complete(postId: Long, places: List<PlaceCandidateWithThumbnail>) {
            completed = true
        }

        override fun retry(postId: Long, nextAttemptAt: Instant, reason: String) = Unit

        override fun fail(postId: Long, reason: String) = Unit
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-28T00:00:00Z")
        val PROCESSING_TIMEOUT: Duration = Duration.ofMinutes(1)
    }
}
