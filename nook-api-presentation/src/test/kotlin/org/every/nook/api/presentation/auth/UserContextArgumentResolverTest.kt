package org.every.nook.api.presentation.auth

import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserContextArgumentResolverTest {
    private val resolver = UserContextArgumentResolver()

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
    fun `resolves the dummy user before authentication is implemented`() {
        val parameter = parameterOf(UserContext::class.java)
        val webRequest = mock(NativeWebRequest::class.java)

        val userContext = resolver.resolveArgument(parameter, null, webRequest, null)

        assertEquals(UserContextArgumentResolver.DUMMY_USER_ID, userContext.userId)
    }

    private fun parameterOf(type: Class<*>): MethodParameter = mock(MethodParameter::class.java).also { parameter ->
        `when`(parameter.parameterType).thenReturn(type)
    }
}
