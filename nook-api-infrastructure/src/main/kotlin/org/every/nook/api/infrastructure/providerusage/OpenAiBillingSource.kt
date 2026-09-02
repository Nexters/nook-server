package org.every.nook.api.infrastructure.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderBillingPeriod
import org.every.nook.api.application.providerusage.ExternalProviderBillingSnapshot
import org.every.nook.api.application.providerusage.ExternalProviderBillingSource
import org.every.nook.api.application.providerusage.ExternalProviderBillingSyncResult
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

class OpenAiBillingSource(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val adminKey: String,
) : ExternalProviderBillingSource {
    override val provider: String = PROVIDER
    override val enabled: Boolean = adminKey.isNotBlank()

    override fun fetch(period: ExternalProviderBillingPeriod, now: Instant): ExternalProviderBillingSyncResult {
        check(adminKey.isNotBlank()) { "OpenAI admin key is not configured" }
        val lineItemCosts = linkedMapOf<String, BigDecimal>()
        var page: String? = null
        do {
            val root = get(period, page)
            root.path("data").forEach { bucket ->
                bucket.path("results").forEach { result ->
                    val amount = result.path("amount")
                    val currency = amount.path("currency").asString()
                    check(currency.equals(USD, ignoreCase = true)) {
                        "OpenAI Costs API returned unsupported currency: $currency"
                    }
                    val lineItem = result.path("line_item").nonBlankString() ?: UNATTRIBUTED
                    check(lineItem.length <= MAX_SKU_LENGTH) {
                        "OpenAI Costs API line item exceeds $MAX_SKU_LENGTH characters"
                    }
                    lineItemCosts.merge(lineItem, amount.path("value").decimalValueOrZero(), BigDecimal::add)
                }
            }
            page = if (root.path("has_more").asBoolean(false)) {
                root.path("next_page").nonBlankString()
                    ?: error("OpenAI Costs API omitted next_page")
            } else {
                null
            }
        } while (page != null)

        val totalCost = lineItemCosts.values.fold(BigDecimal.ZERO, BigDecimal::add)
        val snapshots = lineItemCosts.map { (lineItem, cost) ->
            snapshot(lineItem, cost, period, now)
        } + snapshot(ACCOUNT_TOTAL, totalCost, period, now)
        return ExternalProviderBillingSyncResult(provider, snapshots)
    }

    private fun get(period: ExternalProviderBillingPeriod, page: String?): JsonNode {
        val body = restClient.get()
            .uri { builder ->
                builder.path(COSTS_PATH)
                    .queryParam("start_time", period.start.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                    .queryParam("end_time", period.end.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                    .queryParam("bucket_width", "1d")
                    .queryParam("group_by", "line_item")
                    .queryParam("limit", MAX_BUCKETS)
                    .apply { page?.let { queryParam("page", it) } }
                    .build()
            }
            .header(AUTHORIZATION, "Bearer $adminKey")
            .retrieve()
            .body(String::class.java)
            ?: error("OpenAI Costs API returned an empty response")
        return objectMapper.readTree(body)
    }

    private fun snapshot(
        sku: String,
        cost: BigDecimal,
        period: ExternalProviderBillingPeriod,
        now: Instant,
    ): ExternalProviderBillingSnapshot = ExternalProviderBillingSnapshot(
        provider = provider,
        sku = sku,
        period = period,
        usageUnits = BigDecimal.ZERO,
        costUsd = cost,
        source = SOURCE,
        sourceUpdatedAt = now,
    )

    private fun JsonNode.decimalValueOrZero(): BigDecimal = takeUnless { isMissingNode || isNull }
        ?.decimalValue() ?: BigDecimal.ZERO

    private fun JsonNode.nonBlankString(): String? = takeUnless { isMissingNode || isNull }
        ?.asString()?.trim()?.takeIf(String::isNotBlank)

    private companion object {
        const val PROVIDER = "OPENAI_BILLING"
        const val COSTS_PATH = "/v1/organization/costs"
        const val AUTHORIZATION = "Authorization"
        const val USD = "usd"
        const val SOURCE = "OPENAI_COSTS_API"
        const val ACCOUNT_TOTAL = "ACCOUNT_TOTAL"
        const val UNATTRIBUTED = "UNATTRIBUTED"
        const val MAX_SKU_LENGTH = 100
        const val MAX_BUCKETS = 180
    }
}
