package org.every.nook.api.presentation.auth

import org.every.nook.api.application.auth.InvalidAccessTokenException
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class UserContextArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == UserContext::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UserContext {
        val userId = SecurityContextHolder.getContext().authentication
            ?.takeIf { it.isAuthenticated }
            ?.name
            ?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?: throw InvalidAccessTokenException()

        return UserContext(userId = userId)
    }
}
