package org.every.nook.api.config

import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component("adminAccessGuard")
class AdminAccessGuard(private val properties: AdminSecurityProperties) {
    fun isAdmin(authentication: Authentication?): Boolean = authentication
        ?.takeIf(Authentication::isAuthenticated)
        ?.name
        ?.toLongOrNull()
        ?.let(properties.allowedUserIds::contains)
        ?: false
}
