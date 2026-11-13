package org.every.nook.api.presentation.place

import org.every.nook.api.application.place.PlaceSort
import org.every.nook.api.application.place.RecentPlaceCursor
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecentPlaceCursorCodecTest {
    @Test
    fun `round trips a recent place cursor`() {
        val cursor = RecentPlaceCursor(Instant.parse("2026-07-27T00:00:00.123456Z"), 31)

        assertEquals(cursor, RecentPlaceCursorCodec.decode(RecentPlaceCursorCodec.encode(cursor)))
    }

    @Test
    fun `round trips sorted cursors without losing distance precision`() {
        val timestamp = Instant.parse("2026-07-27T00:00:00.123456Z")
        listOf(
            RecentPlaceCursor(timestamp, 31, PlaceSort.OLDEST),
            RecentPlaceCursor(
                bookmarkedAt = timestamp,
                bookmarkId = 31,
                sort = PlaceSort.NEAREST,
                latitude = BigDecimal("37.5"),
                longitude = BigDecimal("127"),
                distanceScore = 0.000012345678901234,
            ),
        ).forEach { cursor ->
            assertEquals(cursor, RecentPlaceCursorCodec.decode(RecentPlaceCursorCodec.encode(cursor)))
        }
    }

    @Test
    fun `rejects an invalid recent place cursor`() {
        assertFailsWith<IllegalArgumentException> {
            RecentPlaceCursorCodec.decode("invalid")
        }
    }
}
