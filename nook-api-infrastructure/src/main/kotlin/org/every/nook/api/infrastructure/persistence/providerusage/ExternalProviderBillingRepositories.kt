package org.every.nook.api.infrastructure.persistence.providerusage

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ExternalProviderBillingSnapshotJpaRepository :
    JpaRepository<ExternalProviderBillingSnapshotEntity, Long> {
    fun findByProviderAndSkuAndPeriodStartAndPeriodEnd(
        provider: String,
        sku: String,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): ExternalProviderBillingSnapshotEntity?

    fun findAllByPeriodStartAndPeriodEnd(
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): List<ExternalProviderBillingSnapshotEntity>
}

interface ExternalProviderBillingSyncStateJpaRepository :
    JpaRepository<ExternalProviderBillingSyncStateEntity, Long> {
    fun findByProvider(provider: String): ExternalProviderBillingSyncStateEntity?
}
