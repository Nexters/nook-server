package org.every.nook.api.logging

import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.servlet.HandlerMapping
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestContextInterceptorTest {
    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    @Test
    fun `adds authenticated user id and route to MDC before controller handling`() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("42", "credentials").apply {
            isAuthenticated = true
        }
        val request = MockHttpServletRequest("GET", "/api/v1/members/me").apply {
            setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/members/me")
        }

        RequestContextInterceptor().preHandle(request, MockHttpServletResponse(), Any())

        assertEquals("42", MDC.get(RequestLoggingFields.USER_ID))
        assertEquals("/api/v1/members/me", MDC.get(RequestLoggingFields.HTTP_ROUTE))
        assertEquals("GET /api/v1/members/me", MDC.get(RequestLoggingFields.TRANSACTION_NAME))
    }
}
