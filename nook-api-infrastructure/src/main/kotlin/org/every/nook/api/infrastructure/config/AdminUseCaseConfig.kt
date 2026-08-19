package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPlaceQueryPort
import org.every.nook.api.application.admin.AdminPostPlaceCorrectionPort
import org.every.nook.api.application.admin.AdminPostQueryPort
import org.every.nook.api.application.admin.GetAdminPostUseCase
import org.every.nook.api.application.admin.ListAdminAuditLogsUseCase
import org.every.nook.api.application.admin.ListAdminPostsUseCase
import org.every.nook.api.application.admin.ReplaceAdminPostPlacesUseCase
import org.every.nook.api.application.admin.SearchAdminPlacesUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AdminUseCaseConfig {
    @Bean
    fun listAdminPostsUseCase(port: AdminPostQueryPort) = ListAdminPostsUseCase(port)

    @Bean
    fun getAdminPostUseCase(port: AdminPostQueryPort) = GetAdminPostUseCase(port)

    @Bean
    fun searchAdminPlacesUseCase(port: AdminPlaceQueryPort) = SearchAdminPlacesUseCase(port)

    @Bean
    fun replaceAdminPostPlacesUseCase(port: AdminPostPlaceCorrectionPort) = ReplaceAdminPostPlacesUseCase(port)

    @Bean
    fun listAdminAuditLogsUseCase(port: AdminAuditLogPort) = ListAdminAuditLogsUseCase(port)
}
