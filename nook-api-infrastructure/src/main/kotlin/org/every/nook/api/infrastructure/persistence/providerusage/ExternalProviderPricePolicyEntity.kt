package org.every.nook.api.infrastructure.persistence.providerusage

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.every.nook.api.infrastructure.persistence.BaseEntity
import java.math.BigDecimal

@Entity
@Table(name = "external_provider_price_policies")
class ExternalProviderPricePolicyEntity(
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "sku", nullable = false, length = 100)
    val sku: String,
    @Column(name = "unit_type", nullable = false, length = 40)
    val unitType: String,
    @Column(name = "source_currency", nullable = false, length = 3)
    val sourceCurrency: String,
    @Column(name = "source_unit_price", nullable = false, precision = 19, scale = 8)
    val sourceUnitPrice: BigDecimal,
    @Column(name = "unit_size", nullable = false, precision = 19, scale = 6)
    val unitSize: BigDecimal,
    @Column(name = "free_monthly_units", nullable = false, precision = 19, scale = 6)
    val freeMonthlyUnits: BigDecimal,
    @Column(name = "source_url", nullable = true, length = 500)
    val sourceUrl: String?,
    @Column(name = "pricing_status", nullable = false, length = 20)
    val pricingStatus: String,
    @Column(name = "enabled", nullable = false)
    val enabled: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set
}
