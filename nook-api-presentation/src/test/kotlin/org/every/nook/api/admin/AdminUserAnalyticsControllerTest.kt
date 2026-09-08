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
            activeDate = LocalDate.parse("2026-09-03"),
        ).success

        assertEquals(LocalDate.parse("2026-09-01"), response?.from)
        assertEquals(LocalDate.parse("2026-09-06"), response?.to)
        assertEquals(LocalDate.parse("2026-09-03"), response?.activeUsers?.asOf)
        assertEquals(5, response?.activationThreshold)
        assertEquals(0, response?.activeUsers?.daily)
        assertEquals(0, response?.activeUsers?.weekly)
        assertEquals(0, response?.activeUsers?.monthly)
        assertEquals(7, response?.report?.events?.size)
        assertEquals("sign_up", response?.report?.events?.first()?.eventName)
        assertEquals(0, response?.report?.summary?.activeUsers)
        assertEquals(0, response?.report?.members?.size)
    }

    @Test
    fun `uses explicit followup end independent of selection and clamps future to today`() {
        val controller = AdminUserAnalyticsController(
            GetUserAnalyticsOverviewUseCase(
                UserAnalyticsEventQueryPort { _, _ -> emptyList() },
                Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC),
            ),
        )
        val response = controller.overview(
            LocalDate.parse("2026-09-01"),
            LocalDate.parse("2026-09-02"),
            1,
            observationEnd = LocalDate.parse("2026-09-30"),
        ).success
        assertEquals(LocalDate.parse("2026-09-02"), response?.to)
        assertEquals(LocalDate.parse("2026-09-08"), response?.observedThrough)
    }
}
