package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.appversion.AppVersionPolicyPort
import org.every.nook.api.application.appversion.GetAppVersionPolicyUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AppVersionPolicyUseCaseConfig {
    @Bean
    fun getAppVersionPolicyUseCase(policyPort: AppVersionPolicyPort) = GetAppVersionPolicyUseCase(policyPort)
}
