package org.every.nook.api.admin

import org.every.nook.api.application.analytics.GetUserAnalyticsOverviewUseCase
import org.every.nook.api.application.analytics.UserAnalyticsEventQueryPort
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminUserAnalyticsControllerTest {
    @Test
    fun `returns requested period and activation threshold`() {
        val controller = AdminUserAnalyticsController(
            GetUserAnalyticsOverviewUseCase(
                UserAnalyticsEventQueryPort { _, _ -> emptyList() },
                Clock.fixed(Instant.parse("2026-09-06T00:00:00Z"), ZoneOffset.UTC),
            ),
        )

        val response = controller.overview(
            from = LocalDate.parse("2026-09-01"),
            to = LocalDate.parse("2026-09-06"),
            activationThreshold = 5,
        ).success

        assertEquals(LocalDate.parse("2026-09-01"), response?.from)
        assertEquals(LocalDate.parse("2026-09-06"), response?.to)
        assertEquals(5, response?.activationThreshold)
        assertEquals(0, response?.activeUsers?.daily)
        assertEquals(0, response?.activeUsers?.weekly)
        assertEquals(0, response?.activeUsers?.monthly)
    }
}
