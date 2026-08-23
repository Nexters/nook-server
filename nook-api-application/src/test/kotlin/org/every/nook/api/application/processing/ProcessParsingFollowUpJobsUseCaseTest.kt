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

    private fun useCase(port: FakeJobPort, failMedia: Boolean = false): ProcessParsingFollowUpJobsUseCase {
        val mediaStorage = PostMediaStoragePort { media ->
            check(!failMedia) { "storage unavailable" }
            media
        }
        return ProcessParsingFollowUpJobsUseCase(
            jobPort = port,
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

    private class FakeJobPort(private val jobs: ClaimedParsingFollowUpJob) : ParsingFollowUpJobPort {
        val completed = mutableListOf<Long>()
        val retried = mutableListOf<Pair<Long, Instant>>()

        override fun enqueue(event: PostMediaStorageRequestedEvent) = Unit
        override fun enqueue(event: org.every.nook.api.application.place.PlaceThumbnailsRequestedEvent) = Unit
        override fun enqueue(event: org.every.nook.api.application.place.PlaceTagsRequestedEvent) = Unit
        override fun claim(limit: Int, processingTimeout: Duration) = listOf(jobs)
        override fun complete(jobId: Long) {
            completed += jobId
        }

        override fun retry(jobId: Long, availableAt: Instant, reason: String) {
            retried += jobId to availableAt
        }
        override fun fail(jobId: Long, reason: String) = Unit
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-23T00:00:00Z")
    }
}
