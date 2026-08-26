package org.every.nook.api.infrastructure.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderBillingPeriod
import org.hamcrest.Matchers.containsString
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ApifyBillingSourceTest {
    @Test
    fun `fetch sums actual actor run costs for the requested calendar month`() {
        val builder = RestClient.builder().baseUrl("https://api.apify.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo(containsString("/v2/acts/actor-one/runs")))
            .andExpect(header("Authorization", "Bearer token"))
            .andRespond(
                withSuccess(
                    """{"data":{"total":2,"count":2,"items":[{"usageTotalUsd":0.2},{"usageTotalUsd":0.3}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )
        val source = ApifyBillingSource(
            builder.build(),
            jacksonObjectMapper(),
            "token",
            listOf(ApifyBillingSource.Actor("INSTAGRAM_SCRAPER", "actor-one")),
        )

        val result = source.fetch(
            ExternalProviderBillingPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01")),
            Instant.parse("2026-08-26T00:00:00Z"),
        )

        assertEquals(BigDecimal("0.5"), result.snapshots.single { it.sku == "INSTAGRAM_SCRAPER" }.costUsd)
        assertEquals(BigDecimal("2"), result.snapshots.single { it.sku == "INSTAGRAM_SCRAPER" }.usageUnits)
        server.verify()
    }
}
