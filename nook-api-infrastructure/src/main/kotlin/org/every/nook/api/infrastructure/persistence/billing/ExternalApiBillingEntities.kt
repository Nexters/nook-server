package org.every.nook.api.infrastructure.persistence.billing

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "external_api_usage_events",
    uniqueConstraints = [UniqueConstraint(name = "idx_u_idempotency_key", columnNames = ["idempotency_key"])],
    indexes = [
        Index(name = "idx_provider_occurred_at", columnList = "provider,occurred_at"),
        Index(name = "idx_sku_occurred_at", columnList = "sku,occurred_at"),
        Index(name = "idx_status_occurred_at", columnList = "status,occurred_at"),
    ],
)
class ExternalApiUsageEventEntity(
    @Column(name = "idempotency_key", nullable = false, length = 100)
    val idempotencyKey: String,
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "sku", nullable = false, length = 100)
    val sku: String,
    @Column(name = "feature", nullable = false, length = 100)
    val feature: String,
    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: UsageEventStatus = UsageEventStatus.RESERVED,
    @Column(name = "estimated_units", nullable = false, precision = 19, scale = 6)
    val estimatedUnits: BigDecimal,
    @Column(name = "actual_units", nullable = true, precision = 19, scale = 6)
    var actualUnits: BigDecimal? = null,
    @Column(name = "estimated_cost_krw", nullable = false, precision = 19, scale = 6)
    val estimatedCostKrw: BigDecimal,
    @Column(name = "actual_cost_krw", nullable = true, precision = 19, scale = 6)
    var actualCostKrw: BigDecimal? = null,
    @Column(name = "unit_price_krw", nullable = false, precision = 19, scale = 6)
    val unitPriceKrw: BigDecimal,
    @Column(name = "price_unit_size", nullable = false, precision = 19, scale = 6)
    val priceUnitSize: BigDecimal,
    @Column(name = "input_tokens", nullable = true)
    var inputTokens: Long? = null,
    @Column(name = "cached_input_tokens", nullable = true)
    var cachedInputTokens: Long? = null,
    @Column(name = "output_tokens", nullable = true)
    var outputTokens: Long? = null,
    @Column(name = "metadata_json", nullable = true, columnDefinition = "JSON")
    val metadataJson: String? = null,
    @Column(name = "failure_code", nullable = true, length = 100)
    var failureCode: String? = null,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
    @Column(name = "settled_at", nullable = true)
    var settledAt: Instant? = null,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

enum class UsageEventStatus { RESERVED, SUCCEEDED, FAILED }

@Entity
@Table(
    name = "external_api_price_policies",
    uniqueConstraints = [UniqueConstraint(name = "idx_u_provider_sku", columnNames = ["provider", "sku"])],
)
class ExternalApiPricePolicyEntity(
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "sku", nullable = false, length = 100)
    val sku: String,
    @Column(name = "unit_price_krw", nullable = false, precision = 19, scale = 6)
    var unitPriceKrw: BigDecimal,
    @Column(name = "unit_size", nullable = false, precision = 19, scale = 6)
    var unitSize: BigDecimal,
    @Column(name = "free_monthly_units", nullable = false, precision = 19, scale = 6)
    var freeMonthlyUnits: BigDecimal = BigDecimal.ZERO,
    @Column(name = "source_url", nullable = true, length = 500)
    var sourceUrl: String? = null,
    @Column(name = "source_currency", nullable = false, length = 3)
    var sourceCurrency: String = "KRW",
    @Column(name = "source_unit_price", nullable = false, precision = 19, scale = 6)
    var sourceUnitPrice: BigDecimal = unitPriceKrw,
    @Column(name = "managed", nullable = false)
    var managed: Boolean = false,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

@Entity
@Table(
    name = "external_api_budget_policies",
    uniqueConstraints = [UniqueConstraint(name = "idx_u_provider", columnNames = ["provider"])],
)
class ExternalApiBudgetPolicyEntity(
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "monthly_budget_krw", nullable = false, precision = 19, scale = 2)
    var monthlyBudgetKrw: BigDecimal,
    @Column(name = "mode", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var mode: BudgetMode = BudgetMode.ALERT_ONLY,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}

enum class BudgetMode { ALERT_ONLY, BLOCK }

@Entity
@Table(
    name = "external_api_budget_alerts",
    uniqueConstraints = [
        UniqueConstraint(
            name = "idx_u_provider_budget_month_threshold",
            columnNames = ["provider", "budget_month", "threshold_percent"],
        ),
    ],
)
class ExternalApiBudgetAlertEntity(
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "budget_month", nullable = false, length = 7)
    val budgetMonth: String,
    @Column(name = "threshold_percent", nullable = false)
    val thresholdPercent: Int,
    @Column(name = "spent_krw", nullable = false, precision = 19, scale = 6)
    val spentKrw: BigDecimal,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}
