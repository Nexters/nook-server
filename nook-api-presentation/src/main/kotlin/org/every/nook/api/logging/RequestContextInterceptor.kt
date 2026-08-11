package org.every.nook.api.logging

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping

@Component
class RequestContextInterceptor : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        route(request)?.let { route ->
            MDC.put(RequestLoggingFields.HTTP_ROUTE, route)
            MDC.put(RequestLoggingFields.TRANSACTION_NAME, "${request.method} $route")
        }

        SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated }
            ?.name
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.let { MDC.put(RequestLoggingFields.USER_ID, it.toString()) }

        return true
    }

    private fun route(request: HttpServletRequest): String? =
        request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
}
