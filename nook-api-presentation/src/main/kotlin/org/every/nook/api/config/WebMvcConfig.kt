package org.every.nook.api.config

import org.every.nook.api.admin.AdminActorArgumentResolver
import org.every.nook.api.logging.RequestContextInterceptor
import org.every.nook.api.presentation.auth.UserContextArgumentResolver
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val userContextArgumentResolver: UserContextArgumentResolver,
    private val requestContextInterceptor: RequestContextInterceptor,
) : WebMvcConfigurer {
    private val adminActorArgumentResolver = AdminActorArgumentResolver()

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(adminActorArgumentResolver)
        resolvers.add(userContextArgumentResolver)
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(requestContextInterceptor)
    }
}
