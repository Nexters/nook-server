package org.every.nook.api.application.analytics

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnalyticsActivityQueryTest {
    private val date = LocalDate.parse("2026-09-09")

    @Test
    fun `date boundaries use KST and allow exactly 90 days`() {
        val query = AnalyticsActivityQuery(date, date)
        assertEquals(Instant.parse("2026-09-08T15:00:00Z"), query.fromInstant)
        assertEquals(Instant.parse("2026-09-09T15:00:00Z"), query.toExclusive)
        AnalyticsActivityQuery(date.minusDays(89), date)
    }

    @Test
    fun `rejects reversed oversized range and invalid pages`() {
        assertFailsWith<IllegalArgumentException> { AnalyticsActivityQuery(date, date.minusDays(1)) }
        assertFailsWith<IllegalArgumentException> { AnalyticsActivityQuery(date.minusDays(90), date) }
        assertFailsWith<IllegalArgumentException> { AnalyticsActivityQuery(date, date, page = -1) }
        assertFailsWith<IllegalArgumentException> { AnalyticsActivityQuery(date, date, size = 0) }
        assertFailsWith<IllegalArgumentException> { AnalyticsActivityQuery(date, date, size = 101) }
        assertFailsWith<IllegalArgumentException> { AnalyticsActivityQuery(date, date, page = Int.MAX_VALUE) }
    }
}
