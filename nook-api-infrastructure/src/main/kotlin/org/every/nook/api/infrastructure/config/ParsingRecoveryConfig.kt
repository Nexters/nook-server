package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.admin.AdminParsingRecoveryPort
import org.every.nook.api.application.admin.AdminPostRecoveryPort
import org.every.nook.api.application.admin.GetAdminFollowUpJobUseCase
import org.every.nook.api.application.admin.GetAdminPostRecoveryUseCase
import org.every.nook.api.application.admin.ListAdminFollowUpJobsUseCase
import org.every.nook.api.application.admin.ListAdminPostRecoveryUseCase
import org.every.nook.api.application.admin.RetryAdminFollowUpJobUseCase
import org.every.nook.api.application.admin.RetryAdminPostRecoveryUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ParsingRecoveryConfig {
    @Bean
    fun getAdminPostRecovery(port: AdminPostRecoveryPort) = GetAdminPostRecoveryUseCase(port)

    @Bean
    fun listAdminPostRecovery(port: AdminPostRecoveryPort) = ListAdminPostRecoveryUseCase(port)

    @Bean
    fun retryAdminPostRecovery(port: AdminPostRecoveryPort) = RetryAdminPostRecoveryUseCase(port)

    @Bean
    fun getAdminFollowUpJob(port: AdminParsingRecoveryPort) = GetAdminFollowUpJobUseCase(port)

    @Bean
    fun listAdminFollowUpJobs(port: AdminParsingRecoveryPort) = ListAdminFollowUpJobsUseCase(port)

    @Bean
    fun retryAdminFollowUpJob(port: AdminParsingRecoveryPort) = RetryAdminFollowUpJobUseCase(port)
}
