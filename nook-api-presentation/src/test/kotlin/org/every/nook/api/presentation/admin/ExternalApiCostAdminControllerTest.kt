package org.every.nook.api.presentation.admin

import org.every.nook.api.application.billing.ExternalApiBudgetStatus
import org.every.nook.api.application.billing.ExternalApiCostDashboard
import org.every.nook.api.application.billing.ExternalApiProviderCost
import org.every.nook.api.application.billing.GetExternalApiCostDashboardUseCase
import org.every.nook.api.application.billing.GetExternalApiCostPoliciesUseCase
import org.every.nook.api.application.billing.GetExternalApiUsageSummaryUseCase
import org.every.nook.api.application.billing.SaveExternalApiBudgetUseCase
import org.every.nook.api.application.billing.SaveExternalApiPriceUseCase
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalApiCostAdminControllerTest {
    @Test
    fun `returns a monthly provider cost dashboard`() {
        val getDashboard = mock(GetExternalApiCostDashboardUseCase::class.java)
        val from = Instant.parse("2026-08-01T00:00:00Z")
        val to = Instant.parse("2026-09-01T00:00:00Z")
        val expectedQuery = org.every.nook.api.application.billing.ExternalApiUsageQuery(from, to)
        `when`(getDashboard(expectedQuery)).thenReturn(
            ExternalApiCostDashboard(
                from,
                to,
                3300,
                BigDecimal("23373"),
                listOf(
                    ExternalApiProviderCost(
                        provider = "google-places",
                        callCount = 3300,
                        estimatedCostKrw = BigDecimal("23373"),
                        monthlyBudgetKrw = BigDecimal("100000"),
                        budgetUsagePercent = BigDecimal("23.37"),
                        budgetMode = "BLOCK",
                        status = ExternalApiBudgetStatus.NORMAL,
                    ),
                ),
            ),
        )
        val controller = ExternalApiCostAdminController(
            getDashboard,
            mock(GetExternalApiUsageSummaryUseCase::class.java),
            mock(GetExternalApiCostPoliciesUseCase::class.java),
            mock(SaveExternalApiPriceUseCase::class.java),
            mock(SaveExternalApiBudgetUseCase::class.java),
            Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
        )

        val response = controller.dashboard(null).success

        assertEquals(3300, response?.totalCallCount)
        assertEquals("google-places", response?.providers?.single()?.provider)
        assertEquals("BLOCK", response?.providers?.single()?.budgetMode)
        verify(getDashboard)(expectedQuery)
    }
}
