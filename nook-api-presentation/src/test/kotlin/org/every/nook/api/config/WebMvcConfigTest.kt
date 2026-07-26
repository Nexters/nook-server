package org.every.nook.api.config

import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class WebMvcConfigTest {
    @Test
    fun `registers the UserContext argument resolver`() {
        val resolver = UserContextArgumentResolver()
        val resolvers = mutableListOf<HandlerMethodArgumentResolver>()

        WebMvcConfig(resolver).addArgumentResolvers(resolvers)

        assertEquals(listOf<HandlerMethodArgumentResolver>(resolver), resolvers)
    }
}
