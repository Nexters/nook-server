package org.every.nook.api.infrastructure.persistence.providerusage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "external_provider_usage_events",
    indexes = [
        Index(name = "idx_occurred_at", columnList = "occurred_at"),
        Index(name = "idx_provider_occurred_at", columnList = "provider, occurred_at"),
        Index(name = "idx_status_occurred_at", columnList = "status, occurred_at"),
    ],
)
class ExternalProviderUsageEntity(
    @Column(name = "invocation_key", nullable = false, length = 120)
    val invocationKey: String,
    @Column(name = "operation_id", nullable = true, length = 120)
    val operationId: String,
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "operation", nullable = false, length = 100)
    val operation: String,
    @Column(name = "sku", nullable = false, length = 100)
    val sku: String,
    @Column(name = "unit_type", nullable = false, length = 30)
    val unitType: String,
    @Column(name = "units", nullable = false, precision = 20, scale = 6)
    val units: BigDecimal,
    @Column(name = "status", nullable = false, length = 20)
    val status: String,
    @Column(name = "runtime", nullable = false, length = 20)
    val runtime: String,
    @Column(name = "flow", nullable = true, length = 50)
    val flow: String?,
    @Column(name = "stage", nullable = true, length = 100)
    val stage: String?,
    @Column(name = "duration_ms", nullable = false)
    val durationMs: Long,
    @Column(name = "http_status", nullable = true)
    val httpStatus: Int?,
    @Column(name = "failure_code", nullable = true, length = 100)
    val failureCode: String?,
    @Column(name = "input_tokens", nullable = true)
    val inputTokens: Long?,
    @Column(name = "cached_input_tokens", nullable = true)
    val cachedInputTokens: Long?,
    @Column(name = "output_tokens", nullable = true)
    val outputTokens: Long?,
    @Column(name = "source_currency", nullable = true, length = 3)
    val sourceCurrency: String?,
    @Column(name = "source_unit_price", nullable = true, precision = 19, scale = 8)
    val sourceUnitPrice: BigDecimal?,
    @Column(name = "price_unit_size", nullable = true, precision = 19, scale = 6)
    val priceUnitSize: BigDecimal?,
    @Column(name = "exchange_rate_krw", nullable = true, precision = 19, scale = 6)
    val exchangeRateKrw: BigDecimal?,
    @Column(name = "estimated_cost_krw", nullable = true, precision = 19, scale = 6)
    val estimatedCostKrw: BigDecimal?,
    @Column(name = "pricing_status", nullable = false, length = 20)
    val pricingStatus: String,
    @Column(name = "request_id", nullable = true, length = 100)
    val requestId: String?,
    @Column(name = "post_id", nullable = true)
    val postId: Long?,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}
