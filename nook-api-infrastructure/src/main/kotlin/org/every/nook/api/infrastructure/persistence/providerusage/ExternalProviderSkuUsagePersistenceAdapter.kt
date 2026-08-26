package org.every.nook.api.infrastructure.persistence.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderLimitSavePort
import org.every.nook.api.application.providerusage.ExternalProviderSkuUsageOverview
import org.every.nook.api.application.providerusage.ExternalProviderSkuUsagePort
import org.every.nook.api.application.providerusage.ExternalProviderSkuUsageQuery
import org.every.nook.api.application.providerusage.SaveExternalProviderLimitCommand
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class ExternalProviderSkuUsagePersistenceAdapter(
    private val usageRepository: ExternalProviderUsageJpaRepository,
    private val priceRepository: ExternalProviderPricePolicyJpaRepository,
    private val limitRepository: ExternalProviderUsageLimitJpaRepository,
    private val billingRepository: ExternalProviderBillingSnapshotJpaRepository,
) : ExternalProviderSkuUsagePort,
    ExternalProviderLimitSavePort {
    @Transactional(readOnly = true)
    override fun get(query: ExternalProviderSkuUsageQuery): ExternalProviderSkuUsageOverview {
        val events = usageRepository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(query.from, query.to)
        val prices = priceRepository.findAllByEnabledTrue().associateBy { it.key() }
        val limits = limitRepository.findAll().groupBy { it.key() }
        val billing = billingRepository.findAllByPeriodStartAndPeriodEnd(
            query.from.atZone(SEOUL).toLocalDate(),
            query.to.atZone(SEOUL).toLocalDate(),
        ).filter { it.provider == APIFY_BILLING && it.sku != ACCOUNT_TOTAL }
            .associateBy { it.sku }
        val keys = (prices.keys + events.map { it.key() } + limits.keys).sortedWith(
            compareBy(
                { it.provider },
                { it.sku },
            ),
        )
        return ExternalProviderSkuUsageOverview(
            from = query.from,
            to = query.to,
            skus = keys.map { key ->
                toView(
                    key,
                    events.filter { it.key() == key },
                    prices[key],
                    limits[key].orEmpty(),
                    billing[key.sku],
                )
            },
        )
    }

    @Transactional
    override fun save(command: SaveExternalProviderLimitCommand): ExternalProviderSkuUsageOverview.Limit {
        require(command.provider.matches(CODE_PATTERN)) { "Invalid provider" }
        require(command.sku.matches(CODE_PATTERN)) { "Invalid SKU" }
        require(command.limitType in LIMIT_TYPES) { "limitType must be CALLS or COST_USD" }
        require(command.monthlyLimit > BigDecimal.ZERO) { "monthlyLimit must be positive" }
        require(
            priceRepository.existsByProviderAndSku(command.provider, command.sku) ||
                usageRepository.existsByProviderAndSku(command.provider, command.sku),
        ) { "Unknown provider SKU" }
        val entity = limitRepository.findByProviderAndSkuAndLimitType(
            command.provider,
            command.sku,
            command.limitType,
        )?.apply { update(command.monthlyLimit, command.enabled) }
            ?: ExternalProviderUsageLimitEntity(
                provider = command.provider,
                sku = command.sku,
                limitType = command.limitType,
                monthlyLimit = command.monthlyLimit,
                enabled = command.enabled,
            )
        val saved = limitRepository.save(entity)
        return saved.toLimit(BigDecimal.ZERO)
    }

    private fun toView(
        key: SkuKey,
        events: List<ExternalProviderUsageEntity>,
        price: ExternalProviderPricePolicyEntity?,
        limits: List<ExternalProviderUsageLimitEntity>,
        billing: ExternalProviderBillingSnapshotEntity?,
    ): ExternalProviderSkuUsageOverview.Sku {
        val calls = events.fold(BigDecimal.ZERO) { total, event -> total + event.units }
        val succeeded = events.filter { it.status == SUCCEEDED }.fold(BigDecimal.ZERO) { total, event ->
            total + event.units
        }
        val free = price?.freeMonthlyUnits ?: BigDecimal.ZERO
        val billable = succeeded.subtract(free).max(BigDecimal.ZERO)
        val calculatedCost = price?.let {
            billable.multiply(it.sourceUnitPrice).divide(it.unitSize, COST_SCALE, RoundingMode.HALF_UP)
        } ?: events.filter { it.status == SUCCEEDED && it.sourceUnitPrice != null }
            .map { requireNotNull(it.sourceUnitPrice).multiply(it.units) }
            .takeIf { it.isNotEmpty() }
            ?.fold(BigDecimal.ZERO, BigDecimal::add)
        val estimatedCost = billing?.costUsd ?: calculatedCost
        return ExternalProviderSkuUsageOverview.Sku(
            provider = key.provider,
            sku = key.sku,
            unitType = price?.unitType ?: events.firstOrNull()?.unitType ?: "CALL",
            calls = calls,
            freeMonthlyUnits = free,
            billableUnits = billable,
            freeQuotaPercent = free.takeIf { it > BigDecimal.ZERO }
                ?.let { succeeded.percentOf(it) },
            estimatedCostUsd = estimatedCost,
            pricingStatus = if (billing != null) {
                OFFICIAL_BILLING
            } else {
                price?.pricingStatus ?: events.firstOrNull()?.pricingStatus ?: UNPRICED
            },
            sourceUnitPrice = price?.sourceUnitPrice,
            priceUnitSize = price?.unitSize,
            sourceUrl = price?.sourceUrl,
            limits = limits.map { limit ->
                val current = if (limit.limitType == CALLS) calls else estimatedCost ?: BigDecimal.ZERO
                limit.toLimit(current)
            }.sortedBy { it.limitType },
        )
    }

    private fun ExternalProviderUsageLimitEntity.toLimit(current: BigDecimal): ExternalProviderSkuUsageOverview.Limit {
        val utilization = current.percentOf(monthlyLimit)
        return ExternalProviderSkuUsageOverview.Limit(
            id = requireNotNull(id),
            limitType = limitType,
            monthlyLimit = monthlyLimit,
            currentValue = current,
            utilizationPercent = utilization,
            enabled = enabled,
            reachedThresholds = THRESHOLDS.filter { utilization >= it.toBigDecimal() },
        )
    }

    private fun BigDecimal.percentOf(limit: BigDecimal): BigDecimal = multiply(HUNDRED)
        .divide(limit, PERCENT_SCALE, RoundingMode.HALF_UP)

    private fun ExternalProviderPricePolicyEntity.key() = SkuKey(provider, sku)
    private fun ExternalProviderUsageLimitEntity.key() = SkuKey(provider, sku)
    private fun ExternalProviderUsageEntity.key() = SkuKey(provider, sku)

    private data class SkuKey(val provider: String, val sku: String)

    private companion object {
        const val SUCCEEDED = "SUCCEEDED"
        const val CALLS = "CALLS"
        const val UNPRICED = "UNPRICED"
        const val OFFICIAL_BILLING = "OFFICIAL_BILLING"
        const val APIFY_BILLING = "APIFY_BILLING"
        const val ACCOUNT_TOTAL = "ACCOUNT_TOTAL"
        const val COST_SCALE = 8
        const val PERCENT_SCALE = 2
        val HUNDRED = BigDecimal("100")
        val THRESHOLDS = listOf(50, 80, 95, 100)
        val LIMIT_TYPES = setOf(CALLS, "COST_USD")
        val CODE_PATTERN = Regex("[A-Z0-9_]{1,100}")
        val SEOUL = java.time.ZoneId.of("Asia/Seoul")
    }
}
