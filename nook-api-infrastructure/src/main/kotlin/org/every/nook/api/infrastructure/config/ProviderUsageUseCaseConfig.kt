package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.providerusage.ExternalProviderCatalogPort
import org.every.nook.api.application.providerusage.GetExternalProviderOverviewUseCase
import org.every.nook.api.application.providerusage.GetOpenAiTokenUsageUseCase
import org.every.nook.api.application.providerusage.OpenAiTokenUsageQueryPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ProviderUsageUseCaseConfig {
    @Bean
    fun getExternalProviderOverviewUseCase(catalog: ExternalProviderCatalogPort) =
        GetExternalProviderOverviewUseCase(catalog)

    @Bean
    fun getOpenAiTokenUsageUseCase(port: OpenAiTokenUsageQueryPort) = GetOpenAiTokenUsageUseCase(port)
}
