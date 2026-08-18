package org.every.nook.api.admin

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

fun interface AdminAccessTokenVerifier {
    fun decode(token: String): Jwt
}

class CloudflareAdminAccessTokenVerifier(private val properties: AdminAccessProperties) : AdminAccessTokenVerifier {
    private val decoder by lazy {
        require(properties.enabled) { "Admin Access must be enabled" }
        val issuer = properties.teamDomain.trimEnd('/')
        require(issuer.isNotBlank()) { "Admin Access team domain must be configured" }
        require(properties.audience.isNotBlank()) { "Admin Access audience must be configured" }
        NimbusJwtDecoder.withJwkSetUri("$issuer/cdn-cgi/access/certs").build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer))
        }
    }

    override fun decode(token: String): Jwt = decoder.decode(token)
}
