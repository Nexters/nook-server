package org.every.nook.api.config

import io.swagger.v3.oas.annotations.Parameter
import org.every.nook.api.logging.RequestLoggingFields
import org.every.nook.api.presentation.auth.UserContext
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.every.nook.api.presentation.error.GlobalExceptionHandler
import org.every.nook.api.presentation.response.ApiResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test

@WebMvcTest(SecurityTestController::class)
@Import(
    SecurityConfig::class,
    WebMvcConfig::class,
    UserContextArgumentResolver::class,
    GlobalExceptionHandler::class,
)
class SecurityConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Test
    fun `authenticated JWT subject becomes the current user id`() {
        mockMvc.get("/test/protected") {
            with(jwt().jwt { it.subject("42") })
        }.andExpect {
            status { isOk() }
            jsonPath("$.resultType") { value("SUCCESS") }
            jsonPath("$.success.userId") { value(42) }
        }
    }

    @Test
    fun `unauthenticated request uses common unauthorized response`() {
        mockMvc.get("/test/protected").andExpect {
            status { isUnauthorized() }
            jsonPath("$.resultType") { value("FAIL") }
            jsonPath("$.error.errorCode") { value("INVALID_ACCESS_TOKEN") }
            jsonPath("$.error.reason") { value("인증 정보가 유효하지 않습니다.") }
        }
    }

    @Test
    fun `prometheus actuator endpoint is allowed without authentication`() {
        mockMvc.get("/actuator/prometheus").andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `local frontend preflight request is allowed without authentication`() {
        mockMvc.perform(
            options("/test/protected")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
        ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                    RequestLoggingFields.REQUEST_ID_HEADER,
                ),
            )
    }

    @Test
    fun `service frontend preflight request is allowed without authentication`() {
        mockMvc.perform(
            options("/test/protected")
                .header(HttpHeaders.ORIGIN, "https://everynook.co.kr")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
        ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://everynook.co.kr"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
    }

    @Test
    fun `service frontend www preflight request is allowed without authentication`() {
        mockMvc.perform(
            options("/test/protected")
                .header(HttpHeaders.ORIGIN, "https://www.everynook.co.kr")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
        ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://www.everynook.co.kr"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
    }

    @Test
    fun `service frontend http preflight request is allowed without authentication`() {
        mockMvc.perform(
            options("/test/protected")
                .header(HttpHeaders.ORIGIN, "http://everynook.co.kr")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
        ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://everynook.co.kr"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
    }

    @Test
    fun `dev frontend https preflight request is allowed without authentication`() {
        mockMvc.perform(
            options("/test/protected")
                .header(HttpHeaders.ORIGIN, "https://app-dev.everynook.co.kr")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
        ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app-dev.everynook.co.kr"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
    }

    @Test
    fun `dev frontend http preflight request is rejected`() {
        mockMvc.perform(
            options("/test/protected")
                .header(HttpHeaders.ORIGIN, "http://app-dev.everynook.co.kr")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
        ).andExpect(status().isForbidden)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }

    @Test
    fun `live frontend https preflight request is allowed without authentication`() {
        mockMvc.perform(
            options("/test/protected")
                .header(HttpHeaders.ORIGIN, "https://app.everynook.co.kr")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
        ).andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.everynook.co.kr"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
    }

    @Test
    fun `live frontend http preflight request is rejected`() {
        mockMvc.perform(
            options("/test/protected")
                .header(HttpHeaders.ORIGIN, "http://app.everynook.co.kr")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()),
        ).andExpect(status().isForbidden)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }
}

@RestController
private class SecurityTestController {
    @GetMapping("/test/protected")
    fun protected(@Parameter(hidden = true) userContext: UserContext): ApiResponse<SecurityTestUserResponse> =
        ApiResponse.success(SecurityTestUserResponse(userContext.userId))

    @GetMapping("/actuator/prometheus")
    fun prometheus(): String = PROMETHEUS_RESPONSE

    private companion object {
        const val PROMETHEUS_RESPONSE = "jvm_memory_used_bytes 1.0"
    }
}

private data class SecurityTestUserResponse(val userId: Long)
