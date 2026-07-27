package org.every.nook.api.config

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

private const val UNAUTHORIZED_BODY =
    "{\"code\":\"INVALID_ACCESS_TOKEN\",\"message\":\"인증 정보가 유효하지 않습니다.\",\"fieldErrors\":[]}"

@Configuration
class SecurityConfig {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
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
                response.status = HttpServletResponse.SC_UNAUTHORIZED
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.characterEncoding = Charsets.UTF_8.name()
                response.writer.write(UNAUTHORIZED_BODY)
            }
        }
        return http.build()
    }
}
