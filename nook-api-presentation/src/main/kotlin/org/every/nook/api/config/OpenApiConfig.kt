package org.every.nook.api.config

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OpenApiProperties::class)
class OpenApiConfig(private val properties: OpenApiProperties = OpenApiProperties()) {
    @Bean
    fun openAPI(): OpenAPI {
        val commonSchemas = ModelConverters.getInstance().readAll(ApiResponse::class.java)

        return OpenAPI()
            .components(
                Components()
                    .schemas(commonSchemas)
                    .addSecuritySchemes(
                        BEARER_AUTH_SCHEME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("서비스 access token을 입력합니다."),
                    ),
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_AUTH_SCHEME))
            .info(
                Info()
                    .title("Nook API")
                    .version("v1"),
            )
            .servers(properties.servers.map { Server().url(it) })
    }

    private companion object {
        const val BEARER_AUTH_SCHEME = "bearerAuth"
    }
}

@ConfigurationProperties("nook.openapi")
data class OpenApiProperties(val servers: List<String> = emptyList())
