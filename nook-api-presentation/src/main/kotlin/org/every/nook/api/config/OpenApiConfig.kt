package org.every.nook.api.config

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI {
        val commonSchemas = ModelConverters.getInstance().readAll(ApiResponse::class.java)

        return OpenAPI()
            .components(Components().schemas(commonSchemas))
            .info(
                Info()
                    .title("Nook API")
                    .version("v1"),
            )
    }
}
