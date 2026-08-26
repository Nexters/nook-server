package org.every.nook.api.application.providerusage

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class ExternalProviderBillingPeriod(val start: LocalDate, val end: LocalDate)

data class ExternalProviderBillingSnapshot(
    val provider: String,
    val sku: String,
    val period: ExternalProviderBillingPeriod,
    val usageUnits: BigDecimal,
    val costUsd: BigDecimal,
    val source: String,
    val sourceUpdatedAt: Instant,
)

data class ExternalProviderBillingSyncResult(
    val provider: String,
    val snapshots: List<ExternalProviderBillingSnapshot>,
)

interface ExternalProviderBillingSource {
    val provider: String

    fun fetch(period: ExternalProviderBillingPeriod, now: Instant): ExternalProviderBillingSyncResult
}

interface ExternalProviderBillingStore {
    fun markAttempted(provider: String, attemptedAt: Instant)

    fun replace(result: ExternalProviderBillingSyncResult, succeededAt: Instant)

    fun markFailed(provider: String, attemptedAt: Instant, message: String)
}

class SyncExternalProviderBillingUseCase(
    private val sources: List<ExternalProviderBillingSource>,
    private val store: ExternalProviderBillingStore,
) {
    operator fun invoke(period: ExternalProviderBillingPeriod, now: Instant): SyncSummary {
        var succeeded = 0
        var failed = 0
        sources.forEach { source ->
            val provider = source.provider
            store.markAttempted(provider, now)
            runCatching {
                val result = source.fetch(period, now)
                store.replace(result, now)
            }.onSuccess {
                succeeded++
            }.onFailure { exception ->
                failed++
                store.markFailed(provider, now, exception.message ?: exception.javaClass.simpleName)
            }
        }
        return SyncSummary(succeeded, failed)
    }

    data class SyncSummary(val succeeded: Int, val failed: Int)
}

data class ExternalProviderBillingOverview(val from: LocalDate, val to: LocalDate, val providers: List<Provider>) {
    data class Provider(
        val provider: String,
        val status: String,
        val lastAttemptedAt: Instant?,
        val lastSucceededAt: Instant?,
        val errorMessage: String?,
        val usageUnits: BigDecimal,
        val actualCostUsd: BigDecimal,
        val source: String?,
        val skus: List<Sku>,
    )

    data class Sku(
        val sku: String,
        val usageUnits: BigDecimal,
        val actualCostUsd: BigDecimal,
        val source: String,
        val sourceUpdatedAt: Instant,
    )
}

fun interface ExternalProviderBillingQueryPort {
    fun get(period: ExternalProviderBillingPeriod): ExternalProviderBillingOverview
}

class GetExternalProviderBillingUseCase(private val port: ExternalProviderBillingQueryPort) {
    operator fun invoke(period: ExternalProviderBillingPeriod) = port.get(period)
}
