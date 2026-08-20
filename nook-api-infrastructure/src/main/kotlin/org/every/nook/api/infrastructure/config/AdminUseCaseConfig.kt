package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPlaceCorrectionPort
import org.every.nook.api.application.admin.AdminPlaceQueryPort
import org.every.nook.api.application.admin.AdminPlaceTagCatalogPort
import org.every.nook.api.application.admin.AdminPostCorrectionPort
import org.every.nook.api.application.admin.AdminPostPlaceCorrectionPort
import org.every.nook.api.application.admin.AdminPostQueryPort
import org.every.nook.api.application.admin.GetAdminPlaceUseCase
import org.every.nook.api.application.admin.GetAdminPostUseCase
import org.every.nook.api.application.admin.ListAdminAuditLogsUseCase
import org.every.nook.api.application.admin.ListAdminPlaceTagsUseCase
import org.every.nook.api.application.admin.ListAdminPlacesUseCase
import org.every.nook.api.application.admin.ListAdminPostsUseCase
import org.every.nook.api.application.admin.ReplaceAdminPostPlacesUseCase
import org.every.nook.api.application.admin.SearchAdminPlacesUseCase
import org.every.nook.api.application.admin.UpdateAdminPlaceTagUseCase
import org.every.nook.api.application.admin.UpdateAdminPlaceUseCase
import org.every.nook.api.application.admin.UpdateAdminPostUseCase
import org.every.nook.api.application.place.PlaceTagCatalogQueryPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AdminUseCaseConfig {
    @Bean
    fun listAdminPostsUseCase(port: AdminPostQueryPort) = ListAdminPostsUseCase(port)

    @Bean
    fun getAdminPostUseCase(port: AdminPostQueryPort) = GetAdminPostUseCase(port)

    @Bean
    fun updateAdminPostUseCase(port: AdminPostCorrectionPort) = UpdateAdminPostUseCase(port)

    @Bean
    fun searchAdminPlacesUseCase(port: AdminPlaceQueryPort) = SearchAdminPlacesUseCase(port)

    @Bean
    fun listAdminPlacesUseCase(port: AdminPlaceQueryPort) = ListAdminPlacesUseCase(port)

    @Bean
    fun getAdminPlaceUseCase(port: AdminPlaceQueryPort) = GetAdminPlaceUseCase(port)

    @Bean
    fun updateAdminPlaceUseCase(port: AdminPlaceCorrectionPort, tagCatalogPort: PlaceTagCatalogQueryPort) =
        UpdateAdminPlaceUseCase(port, tagCatalogPort)

    @Bean
    fun replaceAdminPostPlacesUseCase(port: AdminPostPlaceCorrectionPort) = ReplaceAdminPostPlacesUseCase(port)

    @Bean
    fun listAdminAuditLogsUseCase(port: AdminAuditLogPort) = ListAdminAuditLogsUseCase(port)

    @Bean
    fun listAdminPlaceTagsUseCase(port: AdminPlaceTagCatalogPort) = ListAdminPlaceTagsUseCase(port)

    @Bean
    fun updateAdminPlaceTagUseCase(port: AdminPlaceTagCatalogPort) = UpdateAdminPlaceTagUseCase(port)
}
