package org.every.nook.api.application.billing

import java.math.BigDecimal
import java.time.Instant

interface ExternalApiUsageMeter {
    fun reserve(command: ReserveExternalApiUsage): UsageReservation

    fun settle(command: SettleExternalApiUsage)
}

data class ReserveExternalApiUsage(
    val idempotencyKey: String,
    val provider: String,
    val sku: String,
    val feature: String,
    val estimatedUnits: BigDecimal = BigDecimal.ONE,
    val metadata: Map<String, String> = emptyMap(),
)

data class UsageReservation(val id: Long, val idempotencyKey: String)

data class SettleExternalApiUsage(
    val reservationId: Long,
    val status: ExternalApiUsageStatus,
    val actualUnits: BigDecimal,
    val inputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val outputTokens: Long? = null,
    val failureCode: String? = null,
)

enum class ExternalApiUsageStatus { SUCCEEDED, FAILED }

class ExternalApiBudgetExceededException(provider: String, sku: String) :
    RuntimeException("External API budget exceeded: provider=$provider, sku=$sku")

fun interface ExternalApiUsageQueryPort {
    fun summarize(query: ExternalApiUsageQuery): List<ExternalApiUsageSummary>
}

data class ExternalApiUsageQuery(val from: Instant, val to: Instant, val provider: String? = null)

data class ExternalApiUsageSummary(
    val provider: String,
    val sku: String,
    val feature: String,
    val callCount: Long,
    val totalUnits: BigDecimal,
    val estimatedCostKrw: BigDecimal,
)
