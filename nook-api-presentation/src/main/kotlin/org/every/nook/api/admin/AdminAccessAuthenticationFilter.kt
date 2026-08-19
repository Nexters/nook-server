package org.every.nook.api.admin

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.every.nook.api.application.admin.AdminActor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class AdminAccessAuthenticationFilter(
    private val properties: AdminAccessProperties,
    private val tokenVerifier: AdminAccessTokenVerifier,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.enabled) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin API is disabled")
            return
        }
        val token = request.getHeader(ACCESS_JWT_HEADER)
        val jwt = runCatching { token?.let(tokenVerifier::decode) }.getOrNull()
        val subject = jwt?.subject
        val email = jwt?.getClaimAsString("email")
        if (jwt == null || properties.audience !in jwt.audience) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Cloudflare Access token")
            return
        }
        if (subject.isNullOrBlank() || email.isNullOrBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Cloudflare Access token")
            return
        }
        val actor = AdminActor(subject, email)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated(actor, token, emptyList())
        filterChain.doFilter(request, response)
    }

    private companion object {
        const val ACCESS_JWT_HEADER = "Cf-Access-Jwt-Assertion"
    }
}
