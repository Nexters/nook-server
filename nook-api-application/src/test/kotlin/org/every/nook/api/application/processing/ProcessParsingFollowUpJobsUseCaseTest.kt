package org.every.nook.api.application.processing

import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.every.nook.api.application.place.PlaceTagExtractor
import org.every.nook.api.application.place.PlaceTagSourcePort
import org.every.nook.api.application.place.PlaceTagUpdatePort
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.place.PlaceThumbnailUpdatePort
import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.every.nook.api.application.place.StorePlaceThumbnailUseCase
import org.every.nook.api.application.post.PostMediaStorageRequestedEvent
import org.every.nook.api.application.post.StorePostMediaUseCase
import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.application.post.port.UpdatePostMediaUrlPort
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.domain.post.PostMedia
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessParsingFollowUpJobsUseCaseTest {
    @Test
    fun `completes a claimed media job`() {
        val port = FakeJobPort(mediaJob())

        val count = useCase(port)()

        assertEquals(1, count)
        assertEquals(listOf(1L), port.completed)
        assertEquals(emptyList(), port.retried)
    }

    @Test
    fun `schedules retry when a follow-up handler fails`() {
        val port = FakeJobPort(mediaJob())

        useCase(port, failMedia = true)()

        assertEquals(emptyList(), port.completed)
        assertEquals(listOf(1L to NOW.plusSeconds(10)), port.retried)
    }

    @Test
    fun `stale result does not complete or schedule another retry`() {
        val port = FakeJobPort(mediaJob(), stale = true)
        useCase(port)()
        assertEquals(emptyList(), port.completed)
        assertEquals(emptyList(), port.retried)
        assertEquals(emptyList(), port.failed)
    }

    @Test
    fun `permanent failures terminate without retry`() {
        val port = FakeJobPort(mediaJob())
        useCase(port, failMedia = true, failure = ParsingFailure(ParsingFailureKind.PERMANENT, "gone"))()
        assertEquals(listOf(1L), port.failed)
        assertEquals(emptyList(), port.retried)
    }

    @Test
    fun `rate limit respects provider retry delay`() {
        val port = FakeJobPort(mediaJob())
        val failure = ParsingFailure(ParsingFailureKind.RATE_LIMIT, "limited", Duration.ofMinutes(2))
        useCase(port, failMedia = true, failure = failure)()
        assertEquals(listOf(1L to NOW.plusSeconds(120)), port.retried)
    }

    @Test
    fun `manual recovery starts a fresh retry budget without reusing ownership token`() {
        val port = FakeJobPort(mediaJob().copy(attempt = 20, retryAttempt = 1))
        useCase(port, failMedia = true)()
        assertEquals(listOf(1L to NOW.plusSeconds(10)), port.retried)
    }

    @Test
    fun `exhausted retry budget terminates job`() {
        val port = FakeJobPort(mediaJob().copy(attempt = 5, retryAttempt = 5))
        useCase(port, failMedia = true)()
        assertEquals(listOf(1L), port.failed)
        assertEquals(emptyList(), port.retried)
    }

    private fun useCase(
        port: FakeJobPort,
        failMedia: Boolean = false,
        failure: ParsingFailure? = null,
    ): ProcessParsingFollowUpJobsUseCase {
        val mediaStorage = PostMediaStoragePort { media ->
            check(!failMedia) { "storage unavailable" }
            media
        }
        return ProcessParsingFollowUpJobsUseCase(
            jobPort = port,
            failureClassifier =
            failure?.let { classification -> ParsingFailureClassifier { classification } }
                ?: ParsingFailureClassifier.DEFAULT,
            storePostMedia = StorePostMediaUseCase(mediaStorage, noOpMediaUpdate()),
            storePlaceThumbnail = StorePlaceThumbnailUseCase(PlaceThumbnailProvider { null }, noOpThumbnailUpdate()),
            storePlaceTags = StorePlaceTagsUseCase(
                PlaceTagSourcePort { null },
                PlaceTagExtractor { emptyList() },
                PlaceTagUpdatePort { _, _, _ -> },
                PlaceTagCatalogQueryPort { PlaceTag.defaultDefinitions },
            ),
            batchSize = 10,
            processingTimeout = Duration.ofMinutes(5),
            retryBackoff = Duration.ofSeconds(10),
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )
    }

    private fun noOpMediaUpdate() = UpdatePostMediaUrlPort { _, _, _, _, _, _ -> }

    private fun noOpThumbnailUpdate() = object : PlaceThumbnailUpdatePort {
        override fun update(
            provider: String,
            externalPlaceId: String,
            status: org.every.nook.api.domain.place.PlaceThumbnailParsingStatus,
            supplement: org.every.nook.api.application.place.PlaceSupplement?,
        ) = Unit
    }

    private fun mediaJob() = ClaimedParsingFollowUpJob.Media(
        id = 1,
        attempt = 1,
        event = PostMediaStorageRequestedEvent(11, PostMedia.MediaType.IMAGE.name, "https://source.test/1.jpg", 0),
    )

    private class FakeJobPort(private val jobs: ClaimedParsingFollowUpJob, private val stale: Boolean = false) :
        ParsingFollowUpJobPort {
        val failed = mutableListOf<Long>()
        val completed = mutableListOf<Long>()
        val retried = mutableListOf<Pair<Long, Instant>>()

        override fun enqueue(event: PostMediaStorageRequestedEvent) = Unit
        override fun enqueue(event: org.every.nook.api.application.place.PlaceThumbnailsRequestedEvent) = Unit
        override fun enqueue(event: org.every.nook.api.application.place.PlaceTagsRequestedEvent) = Unit
        private var claimed = false
        override fun claim(limit: Int, processingTimeout: Duration): List<ClaimedParsingFollowUpJob> {
            assertEquals(1, limit, "Claim only the job that will execute immediately")
            if (claimed) {
                check(stale || completed.isNotEmpty() || retried.isNotEmpty() || failed.isNotEmpty())
                return emptyList()
            }
            claimed = true
            return listOf(jobs)
        }
        override fun complete(jobId: Long, attempt: Int): Boolean {
            completed += jobId
            return true
        }

        override fun retry(jobId: Long, attempt: Int, availableAt: Instant, reason: String): Boolean {
            retried += jobId to availableAt
            return true
        }
        override fun fail(jobId: Long, attempt: Int, reason: String): Boolean {
            failed += jobId
            return true
        }
        override fun writeResult(jobId: Long, attempt: Int, change: () -> Unit): Boolean {
            if (stale) return false
            change()
            return true
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-23T00:00:00Z")
    }
}
