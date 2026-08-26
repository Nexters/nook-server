package org.every.nook.api.application.providerusage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GetExternalProviderOverviewUseCaseTest {
    @Test
    fun `returns configured providers without requiring usage events`() {
        val provider = ExternalProviderCatalogEntry(
            provider = "APIFY",
            displayName = "Apify",
            category = "SCRAPING",
            purpose = "Instagram scraping",
            runtimes = listOf("API", "WORKER"),
            credentialConfigured = true,
            operationalState = "ACTIVE",
            stateReason = "configured",
            policy = "primary",
        )

        assertEquals(
            listOf(provider),
            GetExternalProviderOverviewUseCase(ExternalProviderCatalogPort { listOf(provider) })().providers,
        )
    }
}
