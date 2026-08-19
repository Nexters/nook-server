package org.every.nook.api.application.billing

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GetExternalApiUsageSummaryUseCaseTest {
    @Test
    fun `rejects an invalid period`() {
        val useCase = GetExternalApiUsageSummaryUseCase { emptyList() }
        val now = Instant.parse("2026-08-18T00:00:00Z")

        assertFailsWith<IllegalArgumentException> {
            useCase(ExternalApiUsageQuery(from = now, to = now))
        }
    }
}
