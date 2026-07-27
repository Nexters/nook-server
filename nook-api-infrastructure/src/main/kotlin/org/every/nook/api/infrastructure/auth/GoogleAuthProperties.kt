package org.every.nook.api.infrastructure.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth.google")
data class GoogleAuthProperties(var clientId: String = "")
