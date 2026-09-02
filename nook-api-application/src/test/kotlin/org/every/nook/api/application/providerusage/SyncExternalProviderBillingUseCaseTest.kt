package org.every.nook.api.application.providerusage

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncExternalProviderBillingUseCaseTest {
    @Test
    fun `skips disabled billing sources`() {
        val disabled = source("DISABLED", enabled = false)
        val enabled = source("ENABLED", enabled = true)
        val store = RecordingStore()
        val now = Instant.parse("2026-09-02T00:00:00Z")

        val summary = SyncExternalProviderBillingUseCase(listOf(disabled, enabled), store)(PERIOD, now)

        assertEquals(SyncExternalProviderBillingUseCase.SyncSummary(succeeded = 1, failed = 0), summary)
        assertEquals(listOf("ENABLED"), store.attempted)
        assertEquals(listOf("ENABLED"), store.replaced)
    }

    private fun source(name: String, enabled: Boolean) = object : ExternalProviderBillingSource {
        override val provider: String = name
        override val enabled: Boolean = enabled

        override fun fetch(period: ExternalProviderBillingPeriod, now: Instant) =
            ExternalProviderBillingSyncResult(provider, emptyList())
    }

    private class RecordingStore : ExternalProviderBillingStore {
        val attempted = mutableListOf<String>()
        val replaced = mutableListOf<String>()

        override fun markAttempted(provider: String, attemptedAt: Instant) {
            attempted += provider
        }

        override fun replace(result: ExternalProviderBillingSyncResult, succeededAt: Instant) {
            replaced += result.provider
        }

        override fun markFailed(provider: String, attemptedAt: Instant, message: String) = Unit
    }

    private companion object {
        val PERIOD = ExternalProviderBillingPeriod(
            LocalDate.parse("2026-09-01"),
            LocalDate.parse("2026-10-01"),
        )
    }
}
