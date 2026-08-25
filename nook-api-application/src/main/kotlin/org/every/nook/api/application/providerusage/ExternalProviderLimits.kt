package org.every.nook.api.application.providerusage

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ExternalProviderSkuUsageQuery(val from: Instant, val to: Instant)

data class ExternalProviderSkuUsageOverview(val from: Instant, val to: Instant, val skus: List<Sku>) {
    data class Sku(
        val provider: String,
        val sku: String,
        val unitType: String,
        val calls: BigDecimal,
        val freeMonthlyUnits: BigDecimal,
        val billableUnits: BigDecimal,
        val freeQuotaPercent: BigDecimal?,
        val estimatedCostUsd: BigDecimal?,
        val pricingStatus: String,
        val sourceUnitPrice: BigDecimal?,
        val priceUnitSize: BigDecimal?,
        val sourceUrl: String?,
        val limits: List<Limit>,
    )

    data class Limit(
        val id: Long,
        val limitType: String,
        val monthlyLimit: BigDecimal,
        val currentValue: BigDecimal,
        val utilizationPercent: BigDecimal,
        val enabled: Boolean,
        val reachedThresholds: List<Int>,
    )
}

data class SaveExternalProviderLimitCommand(
    val provider: String,
    val sku: String,
    val limitType: String,
    val monthlyLimit: BigDecimal,
    val enabled: Boolean,
)

fun interface ExternalProviderSkuUsagePort {
    fun get(query: ExternalProviderSkuUsageQuery): ExternalProviderSkuUsageOverview
}

fun interface ExternalProviderLimitSavePort {
    fun save(command: SaveExternalProviderLimitCommand): ExternalProviderSkuUsageOverview.Limit
}

class GetExternalProviderSkuUsageUseCase(private val port: ExternalProviderSkuUsagePort) {
    operator fun invoke(query: ExternalProviderSkuUsageQuery) = port.get(query)
}

class SaveExternalProviderLimitUseCase(private val port: ExternalProviderLimitSavePort) {
    operator fun invoke(command: SaveExternalProviderLimitCommand) = port.save(command)
}

data class ExternalProviderLimitAlertCandidate(
    val policyId: Long,
    val provider: String,
    val sku: String,
    val limitType: String,
    val monthlyLimit: BigDecimal,
    val currentValue: BigDecimal,
    val utilizationPercent: BigDecimal,
    val notifiedThresholds: Set<Int>,
)

fun interface ExternalProviderLimitAlertCandidatePort {
    fun find(from: Instant, to: Instant, periodStart: LocalDate): List<ExternalProviderLimitAlertCandidate>
}

fun interface ExternalProviderLimitAlertNotifier {
    fun notify(candidate: ExternalProviderLimitAlertCandidate, thresholdPercent: Int)
}

fun interface ExternalProviderLimitAlertDeliveryPort {
    fun markDelivered(policyId: Long, periodStart: LocalDate, thresholdPercent: Int, notifiedAt: Instant)
}

class EvaluateExternalProviderLimitAlertsUseCase(
    private val candidates: ExternalProviderLimitAlertCandidatePort,
    private val notifier: ExternalProviderLimitAlertNotifier,
    private val delivery: ExternalProviderLimitAlertDeliveryPort,
) {
    operator fun invoke(from: Instant, to: Instant, periodStart: LocalDate, now: Instant): Int {
        var delivered = 0
        candidates.find(from, to, periodStart).forEach { candidate ->
            THRESHOLDS.filter { threshold ->
                candidate.utilizationPercent >= threshold.toBigDecimal() && threshold !in candidate.notifiedThresholds
            }.forEach { threshold ->
                notifier.notify(candidate, threshold)
                delivery.markDelivered(candidate.policyId, periodStart, threshold, now)
                delivered++
            }
        }
        return delivered
    }

    private companion object {
        val THRESHOLDS = listOf(50, 80, 95, 100)
    }
}
