package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.providerusage.ExternalProviderCatalogPort
import org.every.nook.api.application.providerusage.ExternalProviderUsageQueryPort
import org.every.nook.api.application.providerusage.GetExternalProviderOverviewUseCase
import org.every.nook.api.application.providerusage.GetExternalProviderUsageUseCase
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
}
