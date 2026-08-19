package org.every.nook.api.admin

import org.every.nook.api.application.admin.AdminActor
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AdminAccessAuthenticationFilterTest {
    @AfterTest
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `disabled admin API rejects requests`() {
        val filter = AdminAccessAuthenticationFilter(AdminAccessProperties(), AdminAccessTokenVerifier(::jwt))
        val response = execute(filter)

        assertEquals(403, response.status)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `verified Access token exposes admin actor`() {
        val properties = AdminAccessProperties(
            enabled = true,
            teamDomain = "https://team.example.com",
            audience = "admin",
        )
        val filter = AdminAccessAuthenticationFilter(properties, AdminAccessTokenVerifier(::jwt))
        val response = execute(filter, "token")

        assertEquals(200, response.status)
        val authentication = requireNotNull(SecurityContextHolder.getContext().authentication)
        val actor = assertIs<AdminActor>(authentication.principal)
        assertEquals("operator@example.com", actor.email)
        assertEquals("access-user-id", actor.subject)
    }

    @Test
    fun `token for another Access application is rejected`() {
        val properties = AdminAccessProperties(
            enabled = true,
            teamDomain = "https://team.example.com",
            audience = "other",
        )
        val filter = AdminAccessAuthenticationFilter(properties, AdminAccessTokenVerifier(::jwt))

        assertEquals(401, execute(filter, "token").status)
    }

    private fun execute(filter: AdminAccessAuthenticationFilter, token: String? = null): MockHttpServletResponse {
        val request = MockHttpServletRequest("GET", "/api/admin/v1/me")
        token?.let { request.addHeader("Cf-Access-Jwt-Assertion", it) }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return response
    }

    private fun jwt(@Suppress("UNUSED_PARAMETER") token: String): Jwt = Jwt.withTokenValue("verified")
        .header("alg", "RS256")
        .subject("access-user-id")
        .audience(listOf("admin"))
        .claim("email", "operator@example.com")
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(60))
        .build()
}
