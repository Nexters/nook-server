package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.providerusage.EvaluateExternalProviderLimitAlertsUseCase
import org.every.nook.api.application.providerusage.ExternalProviderCatalogPort
import org.every.nook.api.application.providerusage.ExternalProviderLimitAlertCandidatePort
import org.every.nook.api.application.providerusage.ExternalProviderLimitAlertDeliveryPort
import org.every.nook.api.application.providerusage.ExternalProviderLimitAlertNotifier
import org.every.nook.api.application.providerusage.ExternalProviderLimitSavePort
import org.every.nook.api.application.providerusage.ExternalProviderSkuUsagePort
import org.every.nook.api.application.providerusage.ExternalProviderUsageQueryPort
import org.every.nook.api.application.providerusage.GetExternalProviderOverviewUseCase
import org.every.nook.api.application.providerusage.GetExternalProviderSkuUsageUseCase
import org.every.nook.api.application.providerusage.GetExternalProviderUsageUseCase
import org.every.nook.api.application.providerusage.SaveExternalProviderLimitUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProviderUsageUseCaseConfig {
    @Bean
    fun getExternalProviderUsageUseCase(port: ExternalProviderUsageQueryPort) = GetExternalProviderUsageUseCase(port)

    @Bean
    fun getExternalProviderOverviewUseCase(
        catalog: ExternalProviderCatalogPort,
        usage: ExternalProviderUsageQueryPort,
    ) = GetExternalProviderOverviewUseCase(catalog, usage)

    @Bean
    fun getExternalProviderSkuUsageUseCase(port: ExternalProviderSkuUsagePort) =
        GetExternalProviderSkuUsageUseCase(port)

    @Bean
    fun saveExternalProviderLimitUseCase(port: ExternalProviderLimitSavePort) = SaveExternalProviderLimitUseCase(port)

    @Bean
    fun evaluateExternalProviderLimitAlertsUseCase(
        candidates: ExternalProviderLimitAlertCandidatePort,
        notifier: ExternalProviderLimitAlertNotifier,
        delivery: ExternalProviderLimitAlertDeliveryPort,
    ) = EvaluateExternalProviderLimitAlertsUseCase(candidates, notifier, delivery)
}
