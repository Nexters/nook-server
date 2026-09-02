package org.every.nook.api.infrastructure.persistence.providerusage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ExternalProviderBillingSnapshotJpaRepository :
    JpaRepository<ExternalProviderBillingSnapshotEntity, Long> {
    @Modifying
    @Query(
        """
        DELETE FROM ExternalProviderBillingSnapshotEntity snapshot
        WHERE snapshot.provider = :provider
          AND snapshot.periodStart = :periodStart
          AND snapshot.periodEnd = :periodEnd
        """,
    )
    fun deleteAllByProviderAndPeriod(
        @Param("provider") provider: String,
        @Param("periodStart") periodStart: LocalDate,
        @Param("periodEnd") periodEnd: LocalDate,
    ): Int

    fun findAllByPeriodStartAndPeriodEnd(
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): List<ExternalProviderBillingSnapshotEntity>
}

interface ExternalProviderBillingSyncStateJpaRepository :
    JpaRepository<ExternalProviderBillingSyncStateEntity, Long> {
    fun findByProvider(provider: String): ExternalProviderBillingSyncStateEntity?
}
