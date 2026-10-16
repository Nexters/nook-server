package org.every.nook.api.application.admin

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceTagExtractor
import org.every.nook.api.application.place.PlaceTagSource
import org.every.nook.api.application.place.PlaceTagSourcePort
import org.every.nook.api.application.place.PlaceTagUpdatePort
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.place.StorePlaceTagsUseCase
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessPlaceReprocessingUseCaseTest {
    @Test
    fun `place without a linked post still refreshes photos without requiring a post`() {
        val jobs = Jobs()
        val hours = PlaceOpeningHours(timeZone = "Asia/Seoul", weekdayDescriptions = listOf("매일 10:00–18:00"))
        var requested: PlaceThumbnailProvider.Request? = null
        val useCase = processor(
            jobs,
            thumbnail = PlaceThumbnailProvider {
                requested = it
                PlaceSupplement(hours, listOf("https://example.com/new.jpg"))
            },
        )

        assertTrue(useCase())
        val result = requireNotNull(jobs.result)
        assertEquals(hours, result.supplement?.openingHours)
        assertEquals(PlaceReprocessingOutcome.NO_SOURCE, result.tagOutcome)
        assertEquals(null, requireNotNull(requested).sourcePostId)
        assertTrue(requireNotNull(requested).failOnProviderError)
        assertFalse(requireNotNull(requested).postMediaFallbackAllowed)
    }

    @Test
    fun `only the selected place is tagged using its section of a multi place post`() {
        val jobs = Jobs()
        val stored = mutableListOf<Long>()
        val first = candidate("누크 식당")
        val second = candidate("다른 카페")
        val event = PlaceTagsRequestedEvent(
            11,
            listOf(PlaceTagsRequestedEvent.Place(17, first), PlaceTagsRequestedEvent.Place(18, second)),
        )
        val sources = PlaceTagSourcePort {
            PlaceTagSource("누크 식당\n조용하고 데이트하기 좋아요\n다른 카페\n북적이는 핫플이에요", listOf("핫플"))
        }
        var input: PlaceTagExtractor.Request? = null
        val store = StorePlaceTagsUseCase(
            sources,
            PlaceTagExtractor {
                input = it
                emptyList()
            },
            PlaceTagUpdatePort { _, placeId, _ -> stored += placeId },
        )
        val useCase = ProcessPlaceReprocessingUseCase(
            jobs,
            PlaceReprocessingSourcePort { source(first, listOf(event)) },
            PlaceThumbnailProvider { null },
            store,
            sources,
        )

        useCase()
        assertTrue(stored.isEmpty(), "Persistence must wait until the fenced finish transaction")
        val captured = requireNotNull(input).places.single()
        assertEquals("누크 식당\n조용하고 데이트하기 좋아요", captured.body)
        assertTrue(captured.hashtags.isEmpty())
        requireNotNull(jobs.result).tagChanges.forEach { it() }
        assertEquals(listOf(17L), stored)
    }

    @Test
    fun `provider failure is kept separate from absent tag source`() {
        val jobs = Jobs()
        processor(jobs, thumbnail = PlaceThumbnailProvider { error("upstream unavailable") })()

        assertTrue(requireNotNull(jobs.result).supplementFailed)
        assertEquals(PlaceReprocessingOutcome.NO_SOURCE, requireNotNull(jobs.result).tagOutcome)
        assertFalse(jobs.failed)
    }

    @Test
    fun `missing place fails the claimed job without calling providers`() {
        val jobs = Jobs()
        val useCase = ProcessPlaceReprocessingUseCase(
            jobs,
            PlaceReprocessingSourcePort { null },
            PlaceThumbnailProvider { error("must not call") },
            tags(),
            PlaceTagSourcePort { null },
        )
        useCase()
        assertTrue(jobs.failed)
        assertEquals(null, jobs.result)
    }

    private fun processor(jobs: Jobs, thumbnail: PlaceThumbnailProvider) = ProcessPlaceReprocessingUseCase(
        jobs,
        PlaceReprocessingSourcePort { source(candidate()) },
        thumbnail,
        tags(),
        PlaceTagSourcePort { null },
    )

    private fun tags() = StorePlaceTagsUseCase(
        PlaceTagSourcePort { null },
        PlaceTagExtractor { emptyList() },
        PlaceTagUpdatePort { _, _, _ -> },
    )

    private fun source(place: PlaceCandidate, events: List<PlaceTagsRequestedEvent> = emptyList()) =
        PlaceReprocessingSource(place, null, emptyList(), emptyList(), events)

    private fun candidate(name: String = "누크 식당") = PlaceCandidate(
        "KAKAO", "123", name, "서울", BigDecimal("37.5"), BigDecimal("127.0"), null, null, null,
    )

    private class Jobs : PlaceReprocessingPort {
        var result: PlaceReprocessingResult? = null
        var failed = false
        override fun claim() = ClaimedPlaceReprocessing(17, 1)
        override fun finish(job: ClaimedPlaceReprocessing, result: PlaceReprocessingResult): Boolean {
            this.result = result
            return true
        }
        override fun fail(job: ClaimedPlaceReprocessing): Boolean {
            failed = true
            return true
        }
        override fun enqueue(command: RequestPlaceReprocessingCommand): PlaceReprocessingJob = error("unused")
        override fun find(placeId: Long): PlaceReprocessingJob? = null
    }
}
