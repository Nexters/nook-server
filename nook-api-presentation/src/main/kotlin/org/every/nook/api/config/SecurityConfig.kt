package org.every.nook.api.config

import jakarta.servlet.http.HttpServletResponse
import org.every.nook.api.presentation.response.ApiError
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(CorsProperties::class)
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity, objectMapper: ObjectMapper): SecurityFilterChain {
        http.cors { }
        http.csrf { it.disable() }
        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        http.authorizeHttpRequests {
            it.requestMatchers(
                "/api/v1/auth/**",
                "/api/v1/members",
                "/actuator/health/**",
                "/actuator/info",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html",
            ).permitAll()
            it.anyRequest().authenticated()
        }
        http.oauth2ResourceServer { resourceServer ->
            resourceServer.jwt { }
            resourceServer.authenticationEntryPoint { _, response, _ ->
                writeErrorResponse(
                    response = response,
                    status = HttpServletResponse.SC_UNAUTHORIZED,
                    errorCode = "INVALID_ACCESS_TOKEN",
                    reason = "인증 정보가 유효하지 않습니다.",
                    objectMapper = objectMapper,
                )
            }
            resourceServer.accessDeniedHandler { _, response, _ ->
                writeErrorResponse(
                    response = response,
                    status = HttpServletResponse.SC_FORBIDDEN,
                    errorCode = "FORBIDDEN",
                    reason = "접근 권한이 없습니다.",
                    objectMapper = objectMapper,
                )
            }
        }
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(properties: CorsProperties): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = properties.allowedOriginPatterns
            allowedMethods = properties.allowedMethods
            allowedHeaders = properties.allowedHeaders
            exposedHeaders = properties.exposedHeaders
            allowCredentials = properties.allowCredentials
            maxAge = properties.maxAgeSeconds
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    private fun writeErrorResponse(
        response: HttpServletResponse,
        status: Int,
        errorCode: String,
        reason: String,
        objectMapper: ObjectMapper,
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(
            response.writer,
            ApiResponse.fail(
                ApiError(
                    errorCode = errorCode,
                    reason = reason,
                ),
            ),
        )
    }
}
