package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.billing.ExternalApiCostManagementPort
import org.every.nook.api.application.billing.ExternalApiUsageQueryPort
import org.every.nook.api.application.billing.GetExternalApiCostDashboardUseCase
import org.every.nook.api.application.billing.GetExternalApiCostPoliciesUseCase
import org.every.nook.api.application.billing.GetExternalApiUsageSummaryUseCase
import org.every.nook.api.application.billing.SaveExternalApiBudgetUseCase
import org.every.nook.api.application.billing.SaveExternalApiPriceUseCase
import org.every.nook.api.infrastructure.billing.OfficialExternalApiPricingInitializer
import org.every.nook.api.infrastructure.billing.OfficialExternalApiPricingProperties
import org.every.nook.api.infrastructure.persistence.billing.ExternalApiPricePolicyJpaRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.support.TransactionTemplate

@Configuration
@EnableConfigurationProperties(OfficialExternalApiPricingProperties::class)
class ExternalApiBillingConfig {
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    fun officialExternalApiPricingInitializer(
        properties: OfficialExternalApiPricingProperties,
        repository: ExternalApiPricePolicyJpaRepository,
        transactionTemplate: TransactionTemplate,
    ) = OfficialExternalApiPricingInitializer(properties, repository, transactionTemplate)

    @Bean
    fun getExternalApiCostDashboardUseCase(port: ExternalApiCostManagementPort) =
        GetExternalApiCostDashboardUseCase(port)

    @Bean
    fun getExternalApiUsageSummaryUseCase(port: ExternalApiUsageQueryPort) = GetExternalApiUsageSummaryUseCase(port)

    @Bean
    fun getExternalApiCostPoliciesUseCase(port: ExternalApiCostManagementPort) = GetExternalApiCostPoliciesUseCase(port)

    @Bean
    fun saveExternalApiPriceUseCase(port: ExternalApiCostManagementPort) = SaveExternalApiPriceUseCase(port)

    @Bean
    fun saveExternalApiBudgetUseCase(port: ExternalApiCostManagementPort) = SaveExternalApiBudgetUseCase(port)
}
