package org.every.nook.api.application.providerusage

import java.math.BigDecimal
import java.time.Instant

data class ExternalProviderUsageCommand(
    val provider: String,
    val operation: String,
    val sku: String,
    val unitType: String = "REQUEST",
    val units: BigDecimal = BigDecimal.ONE,
    val status: String,
    val runtime: String,
    val durationMs: Long,
    val httpStatus: Int? = null,
    val failureType: String? = null,
    val inputTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val outputTokens: Long? = null,
    val requestId: String? = null,
    val postId: Long? = null,
    val flow: String? = null,
    val stage: String? = null,
    val occurredAt: Instant = Instant.now(),
)

fun interface ExternalProviderUsageRecorder {
    fun record(command: ExternalProviderUsageCommand)
}

data class ExternalProviderUsageQuery(
    val from: Instant,
    val to: Instant,
    val provider: String? = null,
    val status: String? = null,
    val offset: Int = 0,
    val limit: Int = 100,
)

data class ExternalProviderUsageSummary(
    val from: Instant,
    val to: Instant,
    val totalCalls: Long,
    val failedCalls: Long,
    val estimatedCostUsd: BigDecimal?,
    val estimatedCostKrw: BigDecimal?,
    val unpricedCalls: Long,
    val providers: List<ProviderSummary>,
    val recentEvents: List<Event>,
) {
    data class ProviderSummary(
        val provider: String,
        val calls: Long,
        val failures: Long,
        val units: BigDecimal,
        val estimatedCostUsd: BigDecimal?,
        val estimatedCostKrw: BigDecimal?,
        val pricingStatus: String,
        val lastCalledAt: Instant?,
        val lastFailureAt: Instant?,
    )

    data class Event(
        val id: Long,
        val provider: String,
        val operation: String,
        val sku: String,
        val units: BigDecimal,
        val unitType: String,
        val status: String,
        val durationMs: Long,
        val httpStatus: Int?,
        val failureType: String?,
        val estimatedCostUsd: BigDecimal?,
        val estimatedCostKrw: BigDecimal?,
        val pricingStatus: String,
        val occurredAt: Instant,
    )
}

fun interface ExternalProviderUsageQueryPort {
    fun get(query: ExternalProviderUsageQuery): ExternalProviderUsageSummary
}

class GetExternalProviderUsageUseCase(private val port: ExternalProviderUsageQueryPort) {
    operator fun invoke(query: ExternalProviderUsageQuery) = port.get(query)
}

data class ExternalProviderCatalogEntry(
    val provider: String,
    val displayName: String,
    val category: String,
    val purpose: String,
    val runtimes: List<String>,
    val credentialConfigured: Boolean,
    val operationalState: String,
    val stateReason: String,
    val policy: String,
    val pricingStatus: String,
)

fun interface ExternalProviderCatalogPort {
    fun get(): List<ExternalProviderCatalogEntry>
}

data class ExternalProviderOverview(val from: Instant, val to: Instant, val providers: List<Provider>) {
    data class Provider(
        val provider: String,
        val displayName: String,
        val category: String,
        val purpose: String,
        val runtimes: List<String>,
        val credentialConfigured: Boolean,
        val operationalState: String,
        val stateReason: String,
        val policy: String,
        val calls: Long,
        val failures: Long,
        val estimatedCostUsd: BigDecimal?,
        val estimatedCostKrw: BigDecimal?,
        val pricingStatus: String,
        val lastCalledAt: Instant?,
        val lastFailureAt: Instant?,
    )
}

class GetExternalProviderOverviewUseCase(
    private val catalog: ExternalProviderCatalogPort,
    private val usage: ExternalProviderUsageQueryPort,
) {
    operator fun invoke(query: ExternalProviderUsageQuery): ExternalProviderOverview {
        val summaries = usage.get(query).providers.associateBy { it.provider }
        return ExternalProviderOverview(
            from = query.from,
            to = query.to,
            providers = catalog.get().map { entry ->
                val summary = summaries[entry.provider]
                ExternalProviderOverview.Provider(
                    provider = entry.provider,
                    displayName = entry.displayName,
                    category = entry.category,
                    purpose = entry.purpose,
                    runtimes = entry.runtimes,
                    credentialConfigured = entry.credentialConfigured,
                    operationalState = entry.operationalState,
                    stateReason = entry.stateReason,
                    policy = entry.policy,
                    calls = summary?.calls ?: 0,
                    failures = summary?.failures ?: 0,
                    estimatedCostUsd = summary?.estimatedCostUsd,
                    estimatedCostKrw = summary?.estimatedCostKrw,
                    pricingStatus = summary?.pricingStatus ?: entry.pricingStatus,
                    lastCalledAt = summary?.lastCalledAt,
                    lastFailureAt = summary?.lastFailureAt,
                )
            },
        )
    }
}
