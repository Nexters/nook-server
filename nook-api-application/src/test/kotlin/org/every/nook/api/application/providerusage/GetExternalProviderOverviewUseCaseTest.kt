package org.every.nook.api.application.providerusage

import java.math.BigDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetExternalProviderOverviewUseCaseTest {
    @Test
    fun `includes catalog provider without usage`() {
        val catalog = ExternalProviderCatalogPort {
            listOf(
                ExternalProviderCatalogEntry(
                    provider = "CLOVA_OCR",
                    displayName = "Naver CLOVA OCR",
                    category = "OCR·추론",
                    purpose = "이미지 텍스트 추출",
                    runtimes = listOf("WORKER"),
                    credentialConfigured = true,
                    operationalState = "FALLBACK",
                    stateReason = "OCR fallback",
                    policy = "앞선 OCR 실패 시 호출",
                    pricingStatus = "UNPRICED",
                ),
            )
        }
        val usage = ExternalProviderUsageQueryPort { query -> emptyUsage(query) }

        val result = GetExternalProviderOverviewUseCase(catalog, usage)(QUERY)

        assertEquals(1, result.providers.size)
        assertEquals(0, result.providers.single().calls)
        assertEquals("FALLBACK", result.providers.single().operationalState)
    }

    @Test
    fun `joins provider usage and latest status`() {
        val entry = ExternalProviderCatalogEntry(
            "OPENAI",
            "OpenAI",
            "OCR·추론",
            "장소 추론",
            listOf("API", "WORKER"),
            true,
            "ACTIVE",
            "추론에 사용",
            "동시성 제한",
            "PRICED",
        )
        val lastCall = Instant.parse("2026-08-20T01:00:00Z")
        val usage = ExternalProviderUsageQueryPort { query ->
            emptyUsage(query).copy(
                providers = listOf(
                    ExternalProviderUsageSummary.ProviderSummary(
                        provider = "OPENAI",
                        calls = 3,
                        failures = 1,
                        units = BigDecimal("3"),
                        estimatedCostUsd = BigDecimal("0.01"),
                        estimatedCostKrw = null,
                        pricingStatus = "PARTIAL",
                        lastCalledAt = lastCall,
                        lastFailureAt = lastCall,
                    ),
                ),
            )
        }

        val result = GetExternalProviderOverviewUseCase(ExternalProviderCatalogPort { listOf(entry) }, usage)(QUERY)

        assertEquals(3, result.providers.single().calls)
        assertEquals(1, result.providers.single().failures)
        assertEquals(lastCall, result.providers.single().lastFailureAt)
    }

    private fun emptyUsage(query: ExternalProviderUsageQuery) = ExternalProviderUsageSummary(
        from = query.from,
        to = query.to,
        totalCalls = 0,
        failedCalls = 0,
        estimatedCostUsd = null,
        estimatedCostKrw = null,
        unpricedCalls = 0,
        providers = emptyList(),
        recentEvents = emptyList(),
    )

    private companion object {
        val QUERY = ExternalProviderUsageQuery(
            from = Instant.parse("2026-08-01T00:00:00Z"),
            to = Instant.parse("2026-09-01T00:00:00Z"),
        )
    }
}
