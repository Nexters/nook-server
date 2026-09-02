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

class ApifyBillingSource(
    private val restClient: RestClient,
    private val objectMapper: ObjectMapper,
    private val apiToken: String,
    private val actors: List<Actor>,
) : ExternalProviderBillingSource {
    override val provider: String = PROVIDER

    override fun fetch(period: ExternalProviderBillingPeriod, now: Instant): ExternalProviderBillingSyncResult {
        check(apiToken.isNotBlank()) { "Apify API token is not configured" }
        val snapshots = actors.distinctBy { it.actorId }.map { actor -> actorSnapshot(actor, period, now) }
        return ExternalProviderBillingSyncResult(provider, period, snapshots)
    }

    private fun actorSnapshot(
        actor: Actor,
        period: ExternalProviderBillingPeriod,
        now: Instant,
    ): ExternalProviderBillingSnapshot {
        var offset = 0
        var runCount = BigDecimal.ZERO
        var cost = BigDecimal.ZERO
        do {
            val root = get("/v2/acts/{actorId}/runs", actor.actorId) { builder ->
                builder.queryParam("startedAfter", period.start.atStartOfDay().toInstant(ZoneOffset.UTC))
                    .queryParam("startedBefore", period.end.atStartOfDay().toInstant(ZoneOffset.UTC).minusMillis(1))
                    .queryParam("limit", PAGE_SIZE)
                    .queryParam("offset", offset)
                    .queryParam("desc", false)
            }
            val data = root.path("data")
            val items = data.path("items")
            items.forEach { item ->
                runCount += BigDecimal.ONE
                cost += item.path("usageTotalUsd").decimalValueOrZero()
            }
            val count = data.path("count").asInt(0)
            offset += count
            val total = data.path("total").asInt(0)
        } while (count > 0 && offset < total)
        return ExternalProviderBillingSnapshot(
            provider = provider,
            sku = actor.sku,
            period = period,
            usageUnits = runCount,
            costUsd = cost,
            source = ACTOR_RUNS_SOURCE,
            sourceUpdatedAt = now,
        )
    }

    private fun get(
        path: String,
        vararg variables: Any,
        configure: (org.springframework.web.util.UriBuilder) -> org.springframework.web.util.UriBuilder,
    ): JsonNode {
        val body = restClient.get()
            .uri { builder -> configure(builder.path(path)).build(*variables) }
            .header(AUTHORIZATION, "Bearer $apiToken")
            .retrieve()
            .body(String::class.java)
            ?: error("Apify billing API returned an empty response")
        return objectMapper.readTree(body)
    }

    data class Actor(val sku: String, val actorId: String)

    private fun JsonNode.decimalValueOrZero(): BigDecimal = takeUnless { isMissingNode || isNull }
        ?.decimalValue() ?: BigDecimal.ZERO

    private companion object {
        const val PROVIDER = "APIFY_BILLING"
        const val AUTHORIZATION = "Authorization"
        const val PAGE_SIZE = 1000
        const val ACTOR_RUNS_SOURCE = "APIFY_ACTOR_RUNS_API"
    }
}
