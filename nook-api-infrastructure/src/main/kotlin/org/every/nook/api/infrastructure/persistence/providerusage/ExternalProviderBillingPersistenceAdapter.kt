package org.every.nook.api.infrastructure.persistence.providerusage

import org.every.nook.api.application.providerusage.ExternalProviderBillingOverview
import org.every.nook.api.application.providerusage.ExternalProviderBillingPeriod
import org.every.nook.api.application.providerusage.ExternalProviderBillingQueryPort
import org.every.nook.api.application.providerusage.ExternalProviderBillingStore
import org.every.nook.api.application.providerusage.ExternalProviderBillingSyncResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@Component
class ExternalProviderBillingPersistenceAdapter(
    private val snapshots: ExternalProviderBillingSnapshotJpaRepository,
    private val states: ExternalProviderBillingSyncStateJpaRepository,
) : ExternalProviderBillingStore,
    ExternalProviderBillingQueryPort {
    @Transactional
    override fun markAttempted(provider: String, attemptedAt: Instant) {
        val state = states.findByProvider(provider) ?: ExternalProviderBillingSyncStateEntity(provider)
        state.attempted(attemptedAt)
        states.save(state)
    }

    @Transactional
    override fun replace(result: ExternalProviderBillingSyncResult, succeededAt: Instant) {
        snapshots.deleteAllByProviderAndPeriod(result.provider, result.period.start, result.period.end)
        snapshots.saveAll(
            result.snapshots.map { snapshot ->
                ExternalProviderBillingSnapshotEntity(
                    provider = snapshot.provider,
                    sku = snapshot.sku,
                    periodStart = snapshot.period.start,
                    periodEnd = snapshot.period.end,
                    usageUnits = snapshot.usageUnits,
                    costUsd = snapshot.costUsd,
                    source = snapshot.source,
                    sourceUpdatedAt = snapshot.sourceUpdatedAt,
                )
            },
        )
        val state = states.findByProvider(result.provider) ?: ExternalProviderBillingSyncStateEntity(result.provider)
        state.succeeded(succeededAt)
        states.save(state)
    }

    @Transactional
    override fun markFailed(provider: String, attemptedAt: Instant, message: String) {
        val state = states.findByProvider(provider) ?: ExternalProviderBillingSyncStateEntity(provider)
        state.failed(attemptedAt, message)
        states.save(state)
    }

    @Transactional(readOnly = true)
    override fun get(period: ExternalProviderBillingPeriod): ExternalProviderBillingOverview {
        val rows = snapshots.findAllByPeriodStartAndPeriodEnd(period.start, period.end)
        val stateByProvider = states.findAll().associateBy { it.provider }
        val providers = (rows.map { it.provider } + stateByProvider.keys).distinct().sorted()
        return ExternalProviderBillingOverview(
            from = period.start,
            to = period.end,
            providers = providers.map { provider ->
                val providerRows = rows.filter { it.provider == provider }
                val state = stateByProvider[provider]
                ExternalProviderBillingOverview.Provider(
                    provider = provider,
                    status = state?.status ?: "NEVER_SYNCED",
                    lastAttemptedAt = state?.lastAttemptedAt,
                    lastSucceededAt = state?.lastSucceededAt,
                    errorMessage = state?.errorMessage,
                    usageUnits = providerRows.filterNot { it.sku == ACCOUNT_TOTAL }
                        .fold(BigDecimal.ZERO) { total, row -> total + row.usageUnits },
                    actualCostUsd = providerRows.firstOrNull { it.sku == ACCOUNT_TOTAL }?.costUsd
                        ?: providerRows.fold(BigDecimal.ZERO) { total, row -> total + row.costUsd },
                    source = providerRows.firstOrNull()?.source,
                    skus = providerRows.filterNot { it.sku == ACCOUNT_TOTAL }.map { row ->
                        ExternalProviderBillingOverview.Sku(
                            sku = row.sku,
                            usageUnits = row.usageUnits,
                            actualCostUsd = row.costUsd,
                            source = row.source,
                            sourceUpdatedAt = row.sourceUpdatedAt,
                        )
                    }.sortedBy { it.sku },
                )
            },
        )
    }

    private companion object {
        const val ACCOUNT_TOTAL = "ACCOUNT_TOTAL"
    }
}
