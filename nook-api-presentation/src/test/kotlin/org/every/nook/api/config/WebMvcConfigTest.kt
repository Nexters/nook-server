package org.every.nook.api.config

import org.every.nook.api.admin.AdminActorArgumentResolver
import org.every.nook.api.logging.RequestContextInterceptor
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class WebMvcConfigTest {
    @Test
    fun `registers admin and user argument resolvers`() {
        val resolver = UserContextArgumentResolver()
        val resolvers = mutableListOf<HandlerMethodArgumentResolver>()

        WebMvcConfig(resolver, RequestContextInterceptor()).addArgumentResolvers(resolvers)

        assertEquals(AdminActorArgumentResolver::class, resolvers.first()::class)
        assertEquals(resolver, resolvers.last())
    }
}
