package org.every.nook.api.infrastructure.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("auth.apple")
data class AppleAuthProperties(
    var clientId: String = "",
    var teamId: String = "",
    var keyId: String = "",
    var privateKey: String = "",
)
