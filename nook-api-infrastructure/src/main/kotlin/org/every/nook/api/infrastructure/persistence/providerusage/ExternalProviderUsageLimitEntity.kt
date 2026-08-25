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
@Table(name = "external_provider_usage_limits")
class ExternalProviderUsageLimitEntity(
    @Column(name = "provider", nullable = false, length = 50)
    val provider: String,
    @Column(name = "sku", nullable = false, length = 100)
    val sku: String,
    @Column(name = "limit_type", nullable = false, length = 20)
    val limitType: String,
    @Column(name = "monthly_limit", nullable = false, precision = 19, scale = 6)
    var monthlyLimit: BigDecimal,
    @Column(name = "enabled", nullable = false)
    var enabled: Boolean,
) : BaseEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    var id: Long? = null
        protected set

    fun update(monthlyLimit: BigDecimal, enabled: Boolean) {
        this.monthlyLimit = monthlyLimit
        this.enabled = enabled
    }
}
