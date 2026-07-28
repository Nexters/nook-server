package org.every.nook.api.presentation.auth

import org.every.nook.api.application.auth.InvalidAccessTokenException
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.NativeWebRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserContextArgumentResolverTest {
    private val resolver = UserContextArgumentResolver()

    @AfterTest
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `supports UserContext parameters`() {
        val parameter = parameterOf(UserContext::class.java)

        assertTrue(resolver.supportsParameter(parameter))
    }

    @Test
    fun `does not support other parameter types`() {
        val parameter = parameterOf(Long::class.java)

        assertFalse(resolver.supportsParameter(parameter))
    }

    @Test
    fun `resolves the authenticated JWT subject as user id`() {
        val parameter = parameterOf(UserContext::class.java)
        val webRequest = mock(NativeWebRequest::class.java)
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("42", "credentials", "ROLE_USER")

        val userContext = resolver.resolveArgument(parameter, null, webRequest, null)

        assertEquals(42L, userContext.userId)
    }

    @Test
    fun `rejects a missing authentication`() {
        val parameter = parameterOf(UserContext::class.java)
        val webRequest = mock(NativeWebRequest::class.java)

        assertFailsWith<InvalidAccessTokenException> {
            resolver.resolveArgument(parameter, null, webRequest, null)
        }
    }

    @Test
    fun `rejects a non numeric JWT subject`() {
        val parameter = parameterOf(UserContext::class.java)
        val webRequest = mock(NativeWebRequest::class.java)
        SecurityContextHolder.getContext().authentication =
            TestingAuthenticationToken("not-a-member-id", "credentials", "ROLE_USER")

        assertFailsWith<InvalidAccessTokenException> {
            resolver.resolveArgument(parameter, null, webRequest, null)
        }
    }

    private fun parameterOf(type: Class<*>): MethodParameter = mock(MethodParameter::class.java).also { parameter ->
        `when`(parameter.parameterType).thenReturn(type)
    }
}
