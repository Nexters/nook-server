package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminActor
import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.ClaimedPlaceReprocessing
import org.every.nook.api.application.admin.PlaceReprocessingActiveException
import org.every.nook.api.application.admin.PlaceReprocessingOutcome
import org.every.nook.api.application.admin.PlaceReprocessingResult
import org.every.nook.api.application.admin.PlaceReprocessingSource
import org.every.nook.api.application.admin.PlaceReprocessingStatus
import org.every.nook.api.application.admin.RequestPlaceReprocessingCommand
import org.every.nook.api.application.place.PlaceOpeningHours
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceReprocessingAdapterTest {
    private val places = mock(PlaceJpaRepository::class.java)
    private val jobs = mock(PlaceReprocessingJobJpaRepository::class.java)
    private val audit = mock(AdminAuditLogPort::class.java)
    private val logs = mock(AdminAuditLogJpaRepository::class.java)
    private val protection = PlaceReprocessingProtection(logs, jacksonObjectMapper())
    private val adapter = PlaceReprocessingAdapter(jobs, places, audit, protection)
    private val place = PlaceEntity(
        "KAKAO",
        "123",
        "누크 카페",
        "서울",
        latitude = BigDecimal("37.5"),
        longitude = BigDecimal("127.0"),
    )
    private val stored = PlaceReprocessingJobEntity(17, PlaceReprocessingStatus.PROCESSING, 2)
    private val source = PlaceReprocessingSource(place.toCandidate(), null, emptyList(), emptyList(), emptyList())
    private val hours = PlaceOpeningHours(timeZone = "Asia/Seoul", weekdayDescriptions = listOf("매일 영업"))

    @Test
    fun `older execution cannot persist photos or tag changes`() {
        prepare()
        var written = false
        assertFalse(adapter.finish(ClaimedPlaceReprocessing(17, 1), result { written = true }))
        assertFalse(written)
        assertEquals(null, place.thumbnailUrl)
        assertEquals(PlaceReprocessingStatus.PROCESSING, stored.status)
    }

    @Test
    fun `updates only the selected place and exposes missing tag source as partial`() {
        prepare()
        assertTrue(adapter.finish(ClaimedPlaceReprocessing(17, 2), result()))
        assertEquals("https://example.com/photo.jpg", place.thumbnailUrl)
        assertEquals(null, place.openingHours)
        assertEquals(PlaceReprocessingOutcome.UPDATED, stored.photos)
        assertEquals(PlaceReprocessingOutcome.NO_SOURCE, stored.tags)
        assertEquals(PlaceReprocessingStatus.PARTIAL, stored.status)
    }

    @Test
    fun `missing provider data preserves old content and is not complete`() {
        prepare()
        place.thumbnailUrl = "https://example.com/existing.jpg"
        val snapshot = source.copy(thumbnailUrl = place.thumbnailUrl)
        val missing = PlaceReprocessingResult(snapshot, null, false, PlaceReprocessingOutcome.NO_SOURCE, emptyList())
        adapter.finish(ClaimedPlaceReprocessing(17, 2), missing)
        assertEquals("https://example.com/existing.jpg", place.thumbnailUrl)
        assertEquals(PlaceReprocessingOutcome.NO_DATA, stored.photos)
        assertEquals(PlaceReprocessingStatus.PARTIAL, stored.status)
    }

    @Test
    fun `manual clearing before execution protects all explicitly changed fields`() {
        prepare()
        `when`(
            logs.findAllByTargetTypeAndTargetIdAndActionIn(
                "PLACE",
                "17",
                listOf("PLACE_CREATED", "PLACE_BASIC_INFORMATION_UPDATED"),
            ),
        ).thenReturn(
            listOf(
                log(
                    """{"thumbnailUrl":"old","photoUrls":["old"],"openingHours":{},"representativeTags":["QUIET"]}""",
                    """{"thumbnailUrl":null,"photoUrls":[],"openingHours":null,"representativeTags":[]}""",
                ),
            ),
        )
        var written = false
        adapter.finish(ClaimedPlaceReprocessing(17, 2), result { written = true })
        assertFalse(written)
        assertEquals(null, place.thumbnailUrl)
        assertEquals(null, place.openingHours)
        assertEquals(PlaceReprocessingOutcome.PRESERVED, stored.photos)
        assertEquals(PlaceReprocessingOutcome.PRESERVED, stored.tags)
    }

    @Test
    fun `concurrent changes are preserved even without an audit log`() {
        prepare()
        place.thumbnailUrl = "https://example.com/manual.jpg"
        place.openingHours = hours.copy(weekdayDescriptions = listOf("운영자 수정"))
        place.representativeTags = listOf("QUIET")
        var written = false
        adapter.finish(ClaimedPlaceReprocessing(17, 2), result { written = true })
        assertFalse(written)
        assertEquals("https://example.com/manual.jpg", place.thumbnailUrl)
        assertEquals(listOf("운영자 수정"), place.openingHours?.weekdayDescriptions)
        assertEquals(listOf("QUIET"), place.representativeTags)
        assertEquals(PlaceReprocessingStatus.COMPLETED, stored.status)
    }

    @Test
    fun `manual tag edit preserves tags while allowing photo refresh`() {
        prepare()
        place.representativeTags = listOf("QUIET")
        val snapshot = source.copy(representativeTags = place.representativeTags)
        `when`(
            logs.findAllByTargetTypeAndTargetIdAndActionIn(
                "PLACE",
                "17",
                listOf("PLACE_CREATED", "PLACE_BASIC_INFORMATION_UPDATED"),
            ),
        ).thenReturn(listOf(log("""{"representativeTags":[]}""", """{"representativeTags":["QUIET"]}""")))
        var written = false

        adapter.finish(ClaimedPlaceReprocessing(17, 2), result { written = true }.copy(source = snapshot))

        assertFalse(written)
        assertEquals(listOf("QUIET"), place.representativeTags)
        assertEquals(PlaceReprocessingOutcome.PRESERVED, stored.tags)
        assertEquals(PlaceReprocessingOutcome.UPDATED, stored.photos)
        assertEquals("https://example.com/photo.jpg", place.thumbnailUrl)
    }

    @Test
    fun `duplicate manual request is rejected while processing`() {
        prepare()
        assertFailsWith<PlaceReprocessingActiveException> {
            adapter.enqueue(RequestPlaceReprocessingCommand(17, AdminActor("admin", "admin@example.com"), "재시도", null))
        }
    }

    @Test
    fun `changed place identity prevents applying old provider data`() {
        prepare()
        place.name = "수정된 장소명"
        adapter.finish(ClaimedPlaceReprocessing(17, 2), result())
        assertEquals(null, place.thumbnailUrl)
        assertEquals(PlaceReprocessingStatus.FAILED, stored.status)
    }

    private fun prepare() {
        `when`(places.findByIdForUpdate(17)).thenReturn(place)
        `when`(jobs.findForUpdate(17)).thenReturn(stored)
        `when`(
            logs.findAllByTargetTypeAndTargetIdAndActionIn(
                "PLACE",
                "17",
                listOf("PLACE_CREATED", "PLACE_BASIC_INFORMATION_UPDATED"),
            ),
        ).thenReturn(emptyList())
    }

    private fun result(change: (() -> Unit)? = null) = PlaceReprocessingResult(
        source,
        PlaceSupplement(hours, listOf("https://example.com/photo.jpg")),
        false,
        PlaceReprocessingOutcome.NO_SOURCE,
        listOfNotNull(change),
    )

    private fun log(before: String, after: String) = AdminAuditLogEntity(
        "admin", "admin@example.com", "PLACE_BASIC_INFORMATION_UPDATED", "PLACE", "17", "수정", before, after, null,
    )
}
