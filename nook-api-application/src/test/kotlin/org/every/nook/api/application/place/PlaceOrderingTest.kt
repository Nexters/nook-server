package org.every.nook.api.application.place

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaceOrderingTest {
    @Test
    fun `nearest requires a valid GPS pair while other sorts allow no location`() {
        PlaceOrdering().validate()
        PlaceOrdering(PlaceSort.OLDEST).validate()
        PlaceOrdering(PlaceSort.NEAREST, BigDecimal("90"), BigDecimal("180")).validate()
        listOf(
            PlaceOrdering(PlaceSort.NEAREST),
            PlaceOrdering(PlaceSort.NEAREST, BigDecimal.ZERO),
            PlaceOrdering(PlaceSort.NEAREST, BigDecimal("90.01"), BigDecimal.ZERO),
            PlaceOrdering(PlaceSort.NEAREST, BigDecimal.ZERO, BigDecimal("-180.01")),
        ).forEach { ordering -> assertFailsWith<InvalidPlaceSearchRequestException> { ordering.validate() } }
    }

    @Test
    fun `nearest cursor is bound to the same GPS and sort with numeric coordinate equality`() {
        val ordering = PlaceOrdering(PlaceSort.NEAREST, BigDecimal("37.5"), BigDecimal("127"))
        val cursor = RecentPlaceCursor(
            bookmarkedAt = Instant.EPOCH,
            bookmarkId = 1,
            sort = PlaceSort.NEAREST,
            latitude = BigDecimal("37.500"),
            longitude = BigDecimal("127.0"),
            distanceScore = 0.1,
        )
        assertTrue(ordering.matches(cursor))
        assertFalse(ordering.copy(latitude = BigDecimal("37.6")).matches(cursor))
        assertFalse(ordering.copy(sort = PlaceSort.OLDEST).matches(cursor))
        assertFalse(ordering.matches(cursor.copy(distanceScore = null)))
        assertFalse(ordering.matches(cursor.copy(distanceScore = Double.NaN)))
        assertFalse(ordering.matches(cursor.copy(distanceScore = -1.0)))
    }
}
