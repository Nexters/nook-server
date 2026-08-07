package org.every.nook.api.application.place

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceOpeningHoursTest {
    @Test
    fun `calculates current state in the place time zone`() {
        val hours = PlaceOpeningHours(
            timeZone = "Asia/Seoul",
            periods = listOf(
                PlaceOpeningPeriod(
                    open = PlaceOpeningPoint(day = 1, hour = 10, minute = 0),
                    close = PlaceOpeningPoint(day = 1, hour = 22, minute = 0),
                ),
            ),
            weekdayDescriptions = emptyList(),
        )

        assertTrue(hours.isOpenAt(Instant.parse("2026-08-03T03:00:00Z")))
        assertFalse(hours.isOpenAt(Instant.parse("2026-08-03T14:00:00Z")))
    }

    @Test
    fun `supports an opening period crossing midnight`() {
        val hours = PlaceOpeningHours(
            timeZone = "Asia/Seoul",
            periods = listOf(
                PlaceOpeningPeriod(
                    open = PlaceOpeningPoint(day = 6, hour = 22, minute = 0),
                    close = PlaceOpeningPoint(day = 0, hour = 2, minute = 0),
                ),
            ),
            weekdayDescriptions = emptyList(),
        )

        assertTrue(hours.isOpenAt(Instant.parse("2026-08-01T16:00:00Z")))
        assertFalse(hours.isOpenAt(Instant.parse("2026-08-01T18:00:00Z")))
    }
}
