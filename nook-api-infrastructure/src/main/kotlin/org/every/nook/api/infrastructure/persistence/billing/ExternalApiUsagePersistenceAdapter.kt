package org.every.nook.api.infrastructure.persistence.billing

import org.every.nook.api.application.billing.ExternalApiBudgetExceededException
import org.every.nook.api.application.billing.ExternalApiBudgetPolicy
import org.every.nook.api.application.billing.ExternalApiBudgetStatus
import org.every.nook.api.application.billing.ExternalApiCostDashboard
import org.every.nook.api.application.billing.ExternalApiCostManagementPort
import org.every.nook.api.application.billing.ExternalApiCostPolicies
import org.every.nook.api.application.billing.ExternalApiPricePolicy
import org.every.nook.api.application.billing.ExternalApiProviderCost
import org.every.nook.api.application.billing.ExternalApiUsageMeter
import org.every.nook.api.application.billing.ExternalApiUsageQuery
import org.every.nook.api.application.billing.ExternalApiUsageQueryPort
import org.every.nook.api.application.billing.ExternalApiUsageStatus
import org.every.nook.api.application.billing.ExternalApiUsageSummary
import org.every.nook.api.application.billing.ReserveExternalApiUsage
import org.every.nook.api.application.billing.SaveExternalApiBudgetCommand
import org.every.nook.api.application.billing.SaveExternalApiPriceCommand
import org.every.nook.api.application.billing.SettleExternalApiUsage
import org.every.nook.api.application.billing.UsageReservation
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

@Repository
@Suppress("TooManyFunctions") // One transaction boundary owns usage, policy, and dashboard persistence operations.
class ExternalApiUsagePersistenceAdapter(
    private val usageRepository: ExternalApiUsageEventJpaRepository,
    private val priceRepository: ExternalApiPricePolicyJpaRepository,
    private val budgetRepository: ExternalApiBudgetPolicyJpaRepository,
    private val alertRepository: ExternalApiBudgetAlertJpaRepository,
    private val transactionTemplate: TransactionTemplate,
    private val objectMapper: ObjectMapper,
) : ExternalApiUsageMeter,
    ExternalApiUsageQueryPort,
    ExternalApiCostManagementPort {
    private val clock: Clock = Clock.systemUTC()

    override fun reserve(command: ReserveExternalApiUsage): UsageReservation = requireNotNull(
        transactionTemplate.execute {
            usageRepository.findByIdempotencyKey(command.idempotencyKey)?.let {
                return@execute UsageReservation(requireNotNull(it.id), it.idempotencyKey)
            }
            val now = clock.instant()
            val price = priceRepository.findLockedByProviderAndSkuAndEnabledTrue(command.provider, command.sku)
            val unitPrice = price?.unitPriceKrw ?: BigDecimal.ZERO
            val unitSize = price?.unitSize ?: BigDecimal.ONE
            val estimatedCost = price?.let { incrementalCost(command, it, now) }
                ?: BigDecimal.ZERO
            val budgetPolicy = budgetRepository.findLockedByProvider(command.provider)
            enforceBudget(budgetPolicy, command.provider, command.sku, estimatedCost, now)
            val event = usageRepository.saveAndFlush(
                ExternalApiUsageEventEntity(
                    idempotencyKey = command.idempotencyKey,
                    provider = command.provider,
                    sku = command.sku,
                    feature = command.feature,
                    estimatedUnits = command.estimatedUnits,
                    estimatedCostKrw = estimatedCost,
                    unitPriceKrw = unitPrice,
                    priceUnitSize = unitSize,
                    metadataJson = command.metadata.takeIf(Map<String, String>::isNotEmpty)
                        ?.let(objectMapper::writeValueAsString),
                    occurredAt = now,
                ),
            )
            recordAlerts(budgetPolicy, command.provider, now)
            UsageReservation(requireNotNull(event.id), event.idempotencyKey)
        },
    )

    override fun settle(command: SettleExternalApiUsage) {
        transactionTemplate.executeWithoutResult {
            val event = usageRepository.findById(command.reservationId).orElse(null) ?: return@executeWithoutResult
            if (event.status != UsageEventStatus.RESERVED) return@executeWithoutResult
            event.status = when (command.status) {
                ExternalApiUsageStatus.SUCCEEDED -> UsageEventStatus.SUCCEEDED
                ExternalApiUsageStatus.FAILED -> UsageEventStatus.FAILED
            }
            event.actualUnits = command.actualUnits
            event.actualCostKrw = if (event.provider == OPENAI_PROVIDER && command.inputTokens != null) {
                openAiCost(event.sku, command)
            } else if (event.estimatedUnits.signum() == 0) {
                BigDecimal.ZERO
            } else {
                event.estimatedCostKrw.multiply(command.actualUnits)
                    .divide(event.estimatedUnits, COST_SCALE, RoundingMode.HALF_UP)
            }
            event.inputTokens = command.inputTokens
            event.cachedInputTokens = command.cachedInputTokens
            event.outputTokens = command.outputTokens
            event.failureCode = command.failureCode?.take(MAX_FAILURE_CODE_LENGTH)
            event.settledAt = clock.instant()
            usageRepository.save(event)
        }
    }

    override fun summarize(query: ExternalApiUsageQuery): List<ExternalApiUsageSummary> =
        usageRepository.summarize(query.from, query.to, query.provider).map {
            ExternalApiUsageSummary(it.provider, it.sku, it.feature, it.callCount, it.totalUnits, it.estimatedCostKrw)
        }

    override fun dashboard(query: ExternalApiUsageQuery): ExternalApiCostDashboard {
        val summaries = summarize(query)
        val usageByProvider = summaries.groupBy(ExternalApiUsageSummary::provider)
        val budgets = budgetRepository.findAll().associateBy(ExternalApiBudgetPolicyEntity::provider)
        val providers = (usageByProvider.keys + budgets.keys).sorted().map { provider ->
            val usage = usageByProvider[provider].orEmpty()
            val cost = usage.sumOf(ExternalApiUsageSummary::estimatedCostKrw)
            val budget = budgets[provider]
            val percent = budget?.monthlyBudgetKrw?.takeIf { it.signum() > 0 }
                ?.let { cost.multiply(HUNDRED).divide(it, PERCENT_SCALE, RoundingMode.HALF_UP) }
            ExternalApiProviderCost(
                provider = provider,
                callCount = usage.sumOf(ExternalApiUsageSummary::callCount),
                estimatedCostKrw = cost,
                monthlyBudgetKrw = budget?.monthlyBudgetKrw,
                budgetUsagePercent = percent,
                budgetMode = budget?.mode?.name,
                status = budgetStatus(percent),
            )
        }
        return ExternalApiCostDashboard(
            from = query.from,
            to = query.to,
            totalCallCount = providers.sumOf(ExternalApiProviderCost::callCount),
            totalEstimatedCostKrw = providers.sumOf(ExternalApiProviderCost::estimatedCostKrw),
            providers = providers.sortedByDescending(ExternalApiProviderCost::estimatedCostKrw),
        )
    }

    override fun listPolicies(): ExternalApiCostPolicies = ExternalApiCostPolicies(
        prices = priceRepository.findAll().map { it.toView() },
        budgets = budgetRepository.findAll().map { it.toView() },
    )

    override fun savePrice(command: SaveExternalApiPriceCommand): ExternalApiPricePolicy = requireNotNull(
        transactionTemplate.execute {
            val policy = priceRepository.findByProviderAndSku(command.provider, command.sku)
                ?: ExternalApiPricePolicyEntity(
                    command.provider,
                    command.sku,
                    command.unitPriceKrw,
                    command.unitSize,
                    enabled = command.enabled,
                )
            policy.unitPriceKrw = command.unitPriceKrw
            policy.unitSize = command.unitSize
            policy.freeMonthlyUnits = BigDecimal.ZERO
            policy.sourceUrl = null
            policy.sourceCurrency = "KRW"
            policy.sourceUnitPrice = command.unitPriceKrw
            policy.managed = false
            policy.enabled = command.enabled
            priceRepository.save(policy).toView()
        },
    )

    override fun saveBudget(command: SaveExternalApiBudgetCommand): ExternalApiBudgetPolicy = requireNotNull(
        transactionTemplate.execute {
            val policy = budgetRepository.findByProvider(command.provider)
                ?: ExternalApiBudgetPolicyEntity(
                    command.provider,
                    command.monthlyBudgetKrw,
                    BudgetMode.valueOf(command.mode),
                    command.enabled,
                )
            policy.monthlyBudgetKrw = command.monthlyBudgetKrw
            policy.mode = BudgetMode.valueOf(command.mode)
            policy.enabled = command.enabled
            budgetRepository.save(policy).toView()
        },
    )

    private fun ExternalApiPricePolicyEntity.toView() = ExternalApiPricePolicy(
        provider,
        sku,
        unitPriceKrw,
        unitSize,
        freeMonthlyUnits,
        sourceUrl,
        sourceCurrency,
        sourceUnitPrice,
        managed,
        enabled,
    )

    private fun ExternalApiBudgetPolicyEntity.toView() = ExternalApiBudgetPolicy(
        provider,
        monthlyBudgetKrw,
        mode.name,
        enabled,
    )

    private fun budgetStatus(percent: BigDecimal?): ExternalApiBudgetStatus = when {
        percent == null -> ExternalApiBudgetStatus.UNCONFIGURED
        percent >= HUNDRED -> ExternalApiBudgetStatus.EXCEEDED
        percent >= CRITICAL_PERCENT -> ExternalApiBudgetStatus.CRITICAL
        percent >= WARNING_PERCENT -> ExternalApiBudgetStatus.WARNING
        else -> ExternalApiBudgetStatus.NORMAL
    }

    private fun enforceBudget(
        policy: ExternalApiBudgetPolicyEntity?,
        provider: String,
        sku: String,
        estimatedCost: BigDecimal,
        now: Instant,
    ) {
        policy ?: return
        if (policy.mode != BudgetMode.BLOCK) return
        val (from, to) = monthRange(now)
        val projected = usageRepository.sumEstimatedCost(provider, from, to) + estimatedCost
        if (projected > policy.monthlyBudgetKrw) throw ExternalApiBudgetExceededException(provider, sku)
    }

    private fun recordAlerts(policy: ExternalApiBudgetPolicyEntity?, provider: String, now: Instant) {
        policy ?: return
        if (policy.monthlyBudgetKrw.signum() <= 0) return
        val (from, to) = monthRange(now)
        val spent = usageRepository.sumEstimatedCost(provider, from, to)
        val month = YearMonth.from(now.atZone(ZoneOffset.UTC)).toString()
        ALERT_THRESHOLDS.filter { spent * HUNDRED >= policy.monthlyBudgetKrw * it.toBigDecimal() }
            .filterNot { alertRepository.existsByProviderAndBudgetMonthAndThresholdPercent(provider, month, it) }
            .forEach { alertRepository.save(ExternalApiBudgetAlertEntity(provider, month, it, spent)) }
    }

    private fun monthRange(now: Instant): Pair<Instant, Instant> {
        val month = YearMonth.from(now.atZone(ZoneOffset.UTC))
        val from = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return from to month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
    }

    private fun cost(units: BigDecimal, unitPrice: BigDecimal, unitSize: BigDecimal): BigDecimal =
        units.multiply(unitPrice).divide(unitSize, COST_SCALE, RoundingMode.HALF_UP)

    private fun incrementalCost(
        command: ReserveExternalApiUsage,
        policy: ExternalApiPricePolicyEntity,
        now: Instant,
    ): BigDecimal {
        val (from, to) = monthRange(now)
        val previous = usageRepository.sumUnits(command.provider, command.sku, from, to)
        val previouslyBillable = (previous - policy.freeMonthlyUnits).coerceAtLeast(BigDecimal.ZERO)
        val newlyBillable = (previous + command.estimatedUnits - policy.freeMonthlyUnits)
            .coerceAtLeast(BigDecimal.ZERO)
        return cost(newlyBillable - previouslyBillable, policy.unitPriceKrw, policy.unitSize)
    }

    private fun openAiCost(model: String, command: SettleExternalApiUsage): BigDecimal {
        val inputTokens = requireNotNull(command.inputTokens)
        val cachedTokens = command.cachedInputTokens ?: 0
        val uncachedTokens = (inputTokens - cachedTokens).coerceAtLeast(0)
        return tokenCost(model, "input", uncachedTokens) +
            tokenCost(model, "cached-input", cachedTokens) +
            tokenCost(model, "output", command.outputTokens ?: 0)
    }

    private fun tokenCost(model: String, tokenType: String, tokens: Long): BigDecimal {
        val policy = priceRepository.findByProviderAndSku(OPENAI_PROVIDER, "$model-$tokenType")
            ?.takeIf(ExternalApiPricePolicyEntity::enabled)
            ?: return BigDecimal.ZERO
        return cost(BigDecimal.valueOf(tokens), policy.unitPriceKrw, policy.unitSize)
    }

    private companion object {
        val ALERT_THRESHOLDS = listOf(50, 80, 100)
        val HUNDRED = BigDecimal(100)
        const val COST_SCALE = 6
        const val PERCENT_SCALE = 2
        const val MAX_FAILURE_CODE_LENGTH = 100
        val WARNING_PERCENT = BigDecimal(50)
        val CRITICAL_PERCENT = BigDecimal(80)
        const val OPENAI_PROVIDER = "openai"
    }
}
