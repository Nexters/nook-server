package org.every.nook.api.application.billing

import java.math.BigDecimal
import java.time.Instant

interface ExternalApiCostManagementPort {
    fun dashboard(query: ExternalApiUsageQuery): ExternalApiCostDashboard

    fun listPolicies(): ExternalApiCostPolicies

    fun savePrice(command: SaveExternalApiPriceCommand): ExternalApiPricePolicy

    fun saveBudget(command: SaveExternalApiBudgetCommand): ExternalApiBudgetPolicy
}

data class ExternalApiCostDashboard(
    val from: Instant,
    val to: Instant,
    val totalCallCount: Long,
    val totalEstimatedCostKrw: BigDecimal,
    val providers: List<ExternalApiProviderCost>,
)

data class ExternalApiProviderCost(
    val provider: String,
    val callCount: Long,
    val estimatedCostKrw: BigDecimal,
    val monthlyBudgetKrw: BigDecimal?,
    val budgetUsagePercent: BigDecimal?,
    val budgetMode: String?,
    val status: ExternalApiBudgetStatus,
)

enum class ExternalApiBudgetStatus { NORMAL, WARNING, CRITICAL, EXCEEDED, UNCONFIGURED }

data class ExternalApiCostPolicies(
    val prices: List<ExternalApiPricePolicy>,
    val budgets: List<ExternalApiBudgetPolicy>,
)

data class ExternalApiPricePolicy(
    val provider: String,
    val sku: String,
    val unitPriceKrw: BigDecimal,
    val unitSize: BigDecimal,
    val freeMonthlyUnits: BigDecimal,
    val sourceUrl: String?,
    val sourceCurrency: String,
    val sourceUnitPrice: BigDecimal,
    val managed: Boolean,
    val enabled: Boolean,
)

data class ExternalApiBudgetPolicy(
    val provider: String,
    val monthlyBudgetKrw: BigDecimal,
    val mode: String,
    val enabled: Boolean,
)

data class SaveExternalApiPriceCommand(
    val provider: String,
    val sku: String,
    val unitPriceKrw: BigDecimal,
    val unitSize: BigDecimal,
    val enabled: Boolean,
)

data class SaveExternalApiBudgetCommand(
    val provider: String,
    val monthlyBudgetKrw: BigDecimal,
    val mode: String,
    val enabled: Boolean,
)
