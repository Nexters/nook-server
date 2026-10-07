package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.onboarding.GetOnboardingConversionUseCase
import org.every.nook.api.application.onboarding.OnboardingConversionQueryPort
import org.every.nook.api.application.onboarding.OnboardingEventStorePort
import org.every.nook.api.application.onboarding.RecordOnboardingEventUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class OnboardingConfig {
    @Bean
    fun recordOnboardingEventUseCase(store: OnboardingEventStorePort, clock: Clock) =
        RecordOnboardingEventUseCase(store, clock)

    @Bean
    fun getOnboardingConversionUseCase(query: OnboardingConversionQueryPort, clock: Clock) =
        GetOnboardingConversionUseCase(query, clock)
}
