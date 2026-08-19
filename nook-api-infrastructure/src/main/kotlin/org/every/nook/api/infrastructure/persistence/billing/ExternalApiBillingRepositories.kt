package org.every.nook.api.infrastructure.persistence.billing

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.Instant

interface ExternalApiUsageEventJpaRepository : JpaRepository<ExternalApiUsageEventEntity, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): ExternalApiUsageEventEntity?

    @Query(
        "select coalesce(sum(coalesce(e.actualCostKrw, e.estimatedCostKrw)), 0) " +
            "from ExternalApiUsageEventEntity e " +
            "where e.provider = :provider and e.occurredAt >= :from and e.occurredAt < :to",
    )
    fun sumEstimatedCost(
        @Param("provider") provider: String,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): BigDecimal

    @Query(
        "select coalesce(sum(coalesce(e.actualUnits, e.estimatedUnits)), 0) " +
            "from ExternalApiUsageEventEntity e where e.provider = :provider and e.sku = :sku " +
            "and e.occurredAt >= :from and e.occurredAt < :to",
    )
    fun sumUnits(
        @Param("provider") provider: String,
        @Param("sku") sku: String,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): BigDecimal

    @Query(
        "select e.provider as provider, e.sku as sku, e.feature as feature, count(e) as callCount, " +
            "coalesce(sum(coalesce(e.actualUnits, e.estimatedUnits)), 0) as totalUnits, " +
            "coalesce(sum(coalesce(e.actualCostKrw, e.estimatedCostKrw)), 0) as estimatedCostKrw " +
            "from ExternalApiUsageEventEntity e where e.occurredAt >= :from and e.occurredAt < :to " +
            "and (:provider is null or e.provider = :provider) group by e.provider, e.sku, e.feature",
    )
    fun summarize(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("provider") provider: String?,
    ): List<ExternalApiUsageSummaryProjection>
}

interface ExternalApiUsageSummaryProjection {
    val provider: String
    val sku: String
    val feature: String
    val callCount: Long
    val totalUnits: BigDecimal
    val estimatedCostKrw: BigDecimal
}

interface ExternalApiPricePolicyJpaRepository : JpaRepository<ExternalApiPricePolicyEntity, Long> {
    fun findByProviderAndSku(provider: String, sku: String): ExternalApiPricePolicyEntity?

    fun findByProviderAndSkuAndEnabledTrue(provider: String, sku: String): ExternalApiPricePolicyEntity?
}

interface ExternalApiBudgetPolicyJpaRepository : JpaRepository<ExternalApiBudgetPolicyEntity, Long> {
    fun findByProvider(provider: String): ExternalApiBudgetPolicyEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ExternalApiBudgetPolicyEntity b where b.provider = :provider and b.enabled = true")
    fun findLockedByProvider(@Param("provider") provider: String): ExternalApiBudgetPolicyEntity?
}

interface ExternalApiBudgetAlertJpaRepository : JpaRepository<ExternalApiBudgetAlertEntity, Long> {
    fun existsByProviderAndBudgetMonthAndThresholdPercent(
        provider: String,
        budgetMonth: String,
        thresholdPercent: Int,
    ): Boolean
}
