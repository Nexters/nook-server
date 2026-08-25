package org.every.nook.api.infrastructure.persistence.providerusage

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ExternalProviderPricePolicyJpaRepository : JpaRepository<ExternalProviderPricePolicyEntity, Long> {
    fun findAllByEnabledTrue(): List<ExternalProviderPricePolicyEntity>

    fun existsByProviderAndSku(provider: String, sku: String): Boolean
}

interface ExternalProviderUsageLimitJpaRepository : JpaRepository<ExternalProviderUsageLimitEntity, Long> {
    fun findByProviderAndSkuAndLimitType(
        provider: String,
        sku: String,
        limitType: String,
    ): ExternalProviderUsageLimitEntity?

    fun findAllByEnabledTrue(): List<ExternalProviderUsageLimitEntity>
}

interface ExternalProviderLimitNotificationJpaRepository :
    JpaRepository<ExternalProviderLimitNotificationEntity, Long> {
    fun findAllByLimitPolicyIdAndPeriodStart(
        limitPolicyId: Long,
        periodStart: LocalDate,
    ): List<ExternalProviderLimitNotificationEntity>

    fun existsByLimitPolicyIdAndPeriodStartAndThresholdPercent(
        limitPolicyId: Long,
        periodStart: LocalDate,
        thresholdPercent: Int,
    ): Boolean
}
