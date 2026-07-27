package org.every.nook.api.infrastructure.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private const val ACCESS_TOKEN_MINUTES = 30L
private const val REFRESH_TOKEN_DAYS = 30L
private const val SIGNUP_TOKEN_MINUTES = 10L

@ConfigurationProperties("auth.jwt")
data class JwtProperties(
    var issuer: String = "nook-api",
    var accessSecret: String = "",
    var refreshSecret: String = "",
    var accessTtl: Duration = Duration.ofMinutes(ACCESS_TOKEN_MINUTES),
    var refreshTtl: Duration = Duration.ofDays(REFRESH_TOKEN_DAYS),
    var signupTtl: Duration = Duration.ofMinutes(SIGNUP_TOKEN_MINUTES),
)
