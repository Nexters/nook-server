package org.every.nook.api.application.providerusage

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EvaluateExternalProviderLimitAlertsUseCaseTest {
    @Test
    fun `delivers every newly reached threshold in order`() {
        val notified = mutableListOf<Int>()
        val delivered = mutableListOf<Int>()
        val candidate = candidate(utilization = "97", notified = setOf(50))
        val useCase = EvaluateExternalProviderLimitAlertsUseCase(
            ExternalProviderLimitAlertCandidatePort { _, _, _ -> listOf(candidate) },
            ExternalProviderLimitAlertNotifier { _, threshold -> notified += threshold },
            ExternalProviderLimitAlertDeliveryPort { _, _, threshold, _ -> delivered += threshold },
        )

        val count = useCase(FROM, TO, PERIOD_START, NOW)

        assertEquals(2, count)
        assertEquals(listOf(80, 95), notified)
        assertEquals(listOf(80, 95), delivered)
    }

    @Test
    fun `does not mark delivery when Slack fails`() {
        val delivered = mutableListOf<Int>()
        val useCase = EvaluateExternalProviderLimitAlertsUseCase(
            ExternalProviderLimitAlertCandidatePort { _, _, _ -> listOf(candidate("50")) },
            ExternalProviderLimitAlertNotifier { _, _ -> error("Slack unavailable") },
            ExternalProviderLimitAlertDeliveryPort { _, _, threshold, _ -> delivered += threshold },
        )

        assertFailsWith<IllegalStateException> { useCase(FROM, TO, PERIOD_START, NOW) }
        assertEquals(emptyList(), delivered)
    }

    private fun candidate(utilization: String, notified: Set<Int> = emptySet()) = ExternalProviderLimitAlertCandidate(
        policyId = 1,
        provider = "APIFY",
        sku = "INSTAGRAM_SCRAPER",
        limitType = "CALLS",
        monthlyLimit = BigDecimal("1000"),
        currentValue = BigDecimal(utilization).multiply(BigDecimal.TEN),
        utilizationPercent = BigDecimal(utilization),
        notifiedThresholds = notified,
    )

    private companion object {
        val FROM: Instant = Instant.parse("2026-08-01T00:00:00Z")
        val TO: Instant = Instant.parse("2026-09-01T00:00:00Z")
        val NOW: Instant = Instant.parse("2026-08-25T00:00:00Z")
        val PERIOD_START: LocalDate = LocalDate.parse("2026-08-01")
    }
}
