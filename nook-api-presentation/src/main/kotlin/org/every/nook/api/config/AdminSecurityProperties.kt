package org.every.nook.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("admin.security")
data class AdminSecurityProperties(val allowedUserIds: Set<Long> = emptySet())
