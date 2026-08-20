package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.admin.AdminPlaceTagCatalogPort
import org.every.nook.api.application.admin.CreateAdminPlaceTagUseCase
import org.every.nook.api.application.admin.DeleteAdminPlaceTagUseCase
import org.every.nook.api.application.admin.ListAdminPlaceTagsUseCase
import org.every.nook.api.application.admin.ReorderAdminPlaceTagsUseCase
import org.every.nook.api.application.admin.UpdateAdminPlaceTagUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AdminPlaceTagUseCaseConfig {
    @Bean
    fun listAdminPlaceTagsUseCase(port: AdminPlaceTagCatalogPort) = ListAdminPlaceTagsUseCase(port)

    @Bean
    fun updateAdminPlaceTagUseCase(port: AdminPlaceTagCatalogPort) = UpdateAdminPlaceTagUseCase(port)

    @Bean
    fun createAdminPlaceTagUseCase(port: AdminPlaceTagCatalogPort) = CreateAdminPlaceTagUseCase(port)

    @Bean
    fun reorderAdminPlaceTagsUseCase(port: AdminPlaceTagCatalogPort) = ReorderAdminPlaceTagsUseCase(port)

    @Bean
    fun deleteAdminPlaceTagUseCase(port: AdminPlaceTagCatalogPort) = DeleteAdminPlaceTagUseCase(port)
}
