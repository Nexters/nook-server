package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.admin.AdminAppVersionPolicyPort
import org.every.nook.api.application.admin.ListAdminAppVersionPoliciesUseCase
import org.every.nook.api.application.admin.UpsertAdminAppVersionPolicyUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class AdminAppVersionPolicyUseCaseConfig {
    @Bean
    fun listAdminAppVersionPoliciesUseCase(port: AdminAppVersionPolicyPort) = ListAdminAppVersionPoliciesUseCase(port)

    @Bean
    fun upsertAdminAppVersionPolicyUseCase(port: AdminAppVersionPolicyPort) = UpsertAdminAppVersionPolicyUseCase(port)
}
