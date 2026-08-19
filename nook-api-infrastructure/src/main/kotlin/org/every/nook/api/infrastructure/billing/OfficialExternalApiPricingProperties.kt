package org.every.nook.api.infrastructure.billing

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

@ConfigurationProperties("external-api-pricing")
data class OfficialExternalApiPricingProperties(
    val enabled: Boolean = true,
    val usdKrwRate: BigDecimal = BigDecimal("1450"),
) {
    init {
        require(usdKrwRate.signum() > 0) { "USD/KRW rate must be positive" }
    }
}
