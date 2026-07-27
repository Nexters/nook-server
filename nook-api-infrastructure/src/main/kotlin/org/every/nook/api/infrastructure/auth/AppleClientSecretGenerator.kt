package org.every.nook.api.infrastructure.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyFactory
import java.security.interfaces.ECPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date

private const val APPLE_AUDIENCE = "https://appleid.apple.com"

class AppleClientSecretGenerator(private val properties: AppleAuthProperties, private val clock: Clock) {
    fun generate(): String {
        requireConfigured()
        val now = Instant.now(clock)
        val claims = JWTClaimsSet.Builder()
            .issuer(properties.teamId)
            .subject(properties.clientId)
            .audience(APPLE_AUDIENCE)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofMinutes(5))))
            .build()
        return SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.ES256).keyID(properties.keyId).build(),
            claims,
        ).apply {
            sign(ECDSASigner(readPrivateKey()))
        }.serialize()
    }

    private fun readPrivateKey(): ECPrivateKey {
        val normalized = properties.privateKey
            .replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filterNot(Char::isWhitespace)
        val encoded = Base64.getDecoder().decode(normalized)
        return KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(encoded)) as ECPrivateKey
    }

    private fun requireConfigured() {
        require(properties.clientId.isNotBlank()) { "Apple client ID is required" }
        require(properties.teamId.isNotBlank()) { "Apple team ID is required" }
        require(properties.keyId.isNotBlank()) { "Apple key ID is required" }
        require(properties.privateKey.isNotBlank()) { "Apple private key is required" }
    }
}
