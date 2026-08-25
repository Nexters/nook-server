package org.every.nook.api.infrastructure.persistence.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderUsageCommand
import org.every.nook.api.application.providerusage.ExternalProviderUsageQuery
import org.every.nook.api.application.providerusage.ExternalProviderUsageQueryPort
import org.every.nook.api.application.providerusage.ExternalProviderUsageRecorder
import org.every.nook.api.application.providerusage.ExternalProviderUsageSummary
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Component
class ExternalProviderUsagePersistenceAdapter(
    private val repository: ExternalProviderUsageJpaRepository,
    @Value("\${external-provider.pricing.usd-krw-rate:0}") private val usdKrwRate: BigDecimal,
) : ExternalProviderUsageRecorder,
    ExternalProviderUsageQueryPort {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun record(command: ExternalProviderUsageCommand) {
        runCatching { save(command) }.onFailure {
            log.warn(
                "Failed to persist provider usage: provider={}, operation={}",
                command.provider,
                command.operation,
                it,
            )
        }
    }

    private fun save(command: ExternalProviderUsageCommand) {
        val price = OfficialProviderPrices.price(command.provider, command.sku, command.status)
        val convertible = price != null && usdKrwRate > BigDecimal.ZERO
        repository.save(
            ExternalProviderUsageEntity(
                invocationKey = UUID.randomUUID().toString(),
                operationId = UUID.randomUUID().toString(),
                provider = command.provider,
                operation = command.operation,
                sku = command.sku,
                unitType = command.unitType,
                units = command.units,
                status = command.status,
                runtime = command.runtime,
                flow = command.flow,
                stage = command.stage,
                durationMs = command.durationMs,
                httpStatus = command.httpStatus,
                failureCode = command.failureType,
                inputTokens = command.inputTokens,
                cachedInputTokens = command.cachedInputTokens,
                outputTokens = command.outputTokens,
                sourceCurrency = price?.let { USD },
                sourceUnitPrice = price,
                priceUnitSize = BigDecimal.ONE,
                exchangeRateKrw = usdKrwRate.takeIf { it > BigDecimal.ZERO },
                estimatedCostKrw = estimatedCost(price, command.units),
                pricingStatus = pricingStatus(price, convertible),
                requestId = command.requestId,
                postId = command.postId,
                occurredAt = command.occurredAt,
            ),
        )
    }

    private fun estimatedCost(price: BigDecimal?, units: BigDecimal): BigDecimal? =
        price?.multiply(units)?.multiply(usdKrwRate)?.takeIf { usdKrwRate > BigDecimal.ZERO }

    private fun pricingStatus(price: BigDecimal?, convertible: Boolean) = when {
        price == null -> UNPRICED
        !convertible -> UNCONVERTED
        else -> PRICED
    }

    @Transactional(readOnly = true)
    override fun get(query: ExternalProviderUsageQuery): ExternalProviderUsageSummary {
        val all = repository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(query.from, query.to)
            .filter { query.provider == null || it.provider == query.provider }
            .filter { query.status == null || it.status == query.status }
        val recent = repository
            .findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                query.from,
                query.to,
                PageRequest.of(query.offset / query.limit, query.limit),
            )
            .filter { query.provider == null || it.provider == query.provider }
            .filter { query.status == null || it.status == query.status }
        return ExternalProviderUsageSummary(
            from = query.from,
            to = query.to,
            totalCalls = all.sumUnits(),
            failedCalls = all.filter { it.status == FAILED }.sumUnits(),
            estimatedCostUsd = all.sumCostsUsd(),
            estimatedCostKrw = all.sumCosts(),
            unpricedCalls = all.filter { it.pricingStatus == UNPRICED }.sumUnits(),
            providers = all.toProviderSummaries(),
            recentEvents = recent.map { it.toView() },
        )
    }

    private fun List<ExternalProviderUsageEntity>.sumCosts(): BigDecimal? {
        val priced = filter {
            it.status == SUCCEEDED && it.sourceUnitPrice != null && it.exchangeRateKrw != null
        }
        if (priced.isEmpty()) return null
        return priced.groupBy { "${it.provider}:${it.sku}" }.map { (key, events) ->
            val used = events.map { it.units }.fold(BigDecimal.ZERO, BigDecimal::add)
            val billable = used.subtract(OfficialProviderPrices.freeMonthlyUnits(key)).max(BigDecimal.ZERO)
            billable.multiply(requireNotNull(events.first().sourceUnitPrice))
                .multiply(requireNotNull(events.first().exchangeRateKrw))
        }.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    private fun List<ExternalProviderUsageEntity>.sumCostsUsd(): BigDecimal? {
        val priced = filter { it.status == SUCCEEDED && it.sourceUnitPrice != null }
        if (priced.isEmpty()) return null
        return priced.groupBy { "${it.provider}:${it.sku}" }.map { (key, events) ->
            val used = events.map { it.units }.fold(BigDecimal.ZERO, BigDecimal::add)
            val billable = used.subtract(OfficialProviderPrices.freeMonthlyUnits(key)).max(BigDecimal.ZERO)
            billable.multiply(requireNotNull(events.first().sourceUnitPrice))
        }.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    private fun List<ExternalProviderUsageEntity>.sumUnits(): Long =
        map { it.units }.fold(BigDecimal.ZERO, BigDecimal::add).toLong()

    private fun List<ExternalProviderUsageEntity>.toProviderSummaries() = groupBy { it.provider }
        .map { (provider, events) ->
            ExternalProviderUsageSummary.ProviderSummary(
                provider = provider,
                calls = events.sumUnits(),
                failures = events.filter { it.status == FAILED }.sumUnits(),
                units = events.map { it.units }.fold(BigDecimal.ZERO, BigDecimal::add),
                estimatedCostUsd = events.sumCostsUsd(),
                estimatedCostKrw = events.sumCosts(),
                pricingStatus = if (events.all { it.pricingStatus == PRICED }) PRICED else PARTIAL,
                lastCalledAt = events.maxOfOrNull { it.occurredAt },
                lastFailureAt = events.filter { it.status == FAILED }.maxOfOrNull { it.occurredAt },
            )
        }.sortedByDescending { it.calls }

    private fun ExternalProviderUsageEntity.toView() = ExternalProviderUsageSummary.Event(
        id = requireNotNull(id),
        provider = provider,
        operation = operation,
        sku = sku,
        units = units,
        unitType = unitType,
        status = status,
        durationMs = durationMs,
        httpStatus = httpStatus,
        failureType = failureCode,
        estimatedCostUsd = sourceUnitPrice?.multiply(units),
        estimatedCostKrw = estimatedCostKrw,
        pricingStatus = pricingStatus,
        occurredAt = occurredAt,
    )

    private companion object {
        const val USD = "USD"
        const val FAILED = "FAILED"
        const val SUCCEEDED = "SUCCEEDED"
        const val PRICED = "PRICED"
        const val UNPRICED = "UNPRICED"
        const val UNCONVERTED = "UNCONVERTED"
        const val PARTIAL = "PARTIAL"
        val log = LoggerFactory.getLogger(ExternalProviderUsagePersistenceAdapter::class.java)
    }
}

private object OfficialProviderPrices {
    private val perThousand = BigDecimal("1000")
    private val perMillion = BigDecimal("1000000")
    private val prices = mapOf(
        "OPENAI:GPT_5_NANO_INPUT" to BigDecimal("0.05").divide(perMillion),
        "OPENAI:GPT_5_NANO_CACHED_INPUT" to BigDecimal("0.005").divide(perMillion),
        "OPENAI:GPT_5_NANO_OUTPUT" to BigDecimal("0.40").divide(perMillion),
        "GOOGLE_VISION:TEXT_DETECTION" to BigDecimal("1.50").divide(perThousand),
        "GOOGLE_PLACES:NEARBY_SEARCH_PRO" to BigDecimal("32").divide(perThousand),
        "GOOGLE_PLACES:TEXT_SEARCH_PRO" to BigDecimal("32").divide(perThousand),
        "GOOGLE_PLACES:PLACE_DETAILS_PRO" to BigDecimal("17").divide(perThousand),
        "GOOGLE_PLACES:PLACE_DETAILS_PHOTOS" to BigDecimal("7").divide(perThousand),
        "BRIGHT_DATA:WEB_SCRAPER_SUCCESS_RECORD" to BigDecimal("1.50").divide(perThousand),
        "APIFY:INSTAGRAM_SCRAPER" to BigDecimal("2.30").divide(perThousand),
        "APIFY_GOOGLE_MAPS:GOOGLE_MAPS_SCRAPER" to BigDecimal("3.00").divide(perThousand),
        "APIFY_NAVER_PLACE:NAVER_PLACE_PHOTO_SCRAPER" to BigDecimal("0.50").divide(perThousand),
        "APIFY_NAVER_PLACE:NAVER_MAP_SEARCH_RESULTS_SCRAPER" to BigDecimal("1.50").divide(perThousand),
    )
    private val monthlyFreeUnits = mapOf(
        "GOOGLE_VISION:TEXT_DETECTION" to BigDecimal("1000"),
        "GOOGLE_PLACES:NEARBY_SEARCH_PRO" to BigDecimal("5000"),
        "GOOGLE_PLACES:TEXT_SEARCH_PRO" to BigDecimal("5000"),
        "GOOGLE_PLACES:PLACE_DETAILS_PRO" to BigDecimal("5000"),
        "GOOGLE_PLACES:PLACE_DETAILS_PHOTOS" to BigDecimal("1000"),
        "BRIGHT_DATA:WEB_SCRAPER_SUCCESS_RECORD" to BigDecimal("5000"),
    )

    fun price(provider: String, sku: String, status: String): BigDecimal? = prices["$provider:$sku"]
        ?.takeUnless { provider == "BRIGHT_DATA" && status != "SUCCEEDED" }

    fun freeMonthlyUnits(key: String): BigDecimal = monthlyFreeUnits[key] ?: BigDecimal.ZERO
}
