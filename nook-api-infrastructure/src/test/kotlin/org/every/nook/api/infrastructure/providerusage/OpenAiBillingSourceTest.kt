package org.every.nook.api.infrastructure.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderBillingPeriod
import org.hamcrest.Matchers.allOf
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiBillingSourceTest {
    @Test
    fun `fetch aggregates paginated line item costs and account total`() {
        val builder = RestClient.builder().baseUrl("https://api.openai.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        server.expect(requestTo(allOf(containsString("/v1/organization/costs"), containsString("group_by=line_item"))))
            .andExpect(header("Authorization", "Bearer admin-key"))
            .andRespond(
                withSuccess(
                    """
                    {"data":[{"results":[
                      {"amount":{"value":0.2,"currency":"usd"},"line_item":"GPT-5 nano"},
                      {"amount":{"value":0.3,"currency":"usd"},"line_item":"GPT-5 nano"}
                    ]}],"has_more":true,"next_page":"next"}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        server.expect(requestTo(containsString("page=next")))
            .andRespond(
                withSuccess(
                    """
                    {"data":[{"results":[
                      {"amount":{"value":0.4,"currency":"usd"},"line_item":"Image models"}
                    ]}],"has_more":false,"next_page":null}
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON,
                ),
            )
        val source = OpenAiBillingSource(builder.build(), jacksonObjectMapper(), "admin-key")

        val result = source.fetch(
            ExternalProviderBillingPeriod(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-01")),
            Instant.parse("2026-09-01T00:00:00Z"),
        )

        assertTrue(source.enabled)
        assertEquals(BigDecimal("0.5"), result.snapshots.single { it.sku == "GPT-5 nano" }.costUsd)
        assertEquals(BigDecimal("0.4"), result.snapshots.single { it.sku == "Image models" }.costUsd)
        assertEquals(BigDecimal("0.9"), result.snapshots.single { it.sku == "ACCOUNT_TOTAL" }.costUsd)
        assertTrue(result.snapshots.all { it.usageUnits == BigDecimal.ZERO })
        server.verify()
    }

    @Test
    fun `source is disabled without admin key`() {
        val source = OpenAiBillingSource(RestClient.create(), jacksonObjectMapper(), "")

        assertFalse(source.enabled)
    }
}
