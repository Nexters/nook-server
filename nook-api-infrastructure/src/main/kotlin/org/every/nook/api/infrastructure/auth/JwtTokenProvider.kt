package org.every.nook.api.infrastructure.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.MACVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.every.nook.api.application.auth.InvalidRefreshTokenException
import org.every.nook.api.application.auth.IssuedToken
import org.every.nook.api.application.auth.RefreshClaims
import org.every.nook.api.application.auth.port.TokenProvider
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.HexFormat
import java.util.UUID

private const val MIN_SECRET_BYTES = 32
private const val TOKEN_TYPE = "token_type"
private const val ACCESS_TYPE = "access"
private const val REFRESH_TYPE = "refresh"

class JwtTokenProvider(private val properties: JwtProperties, private val clock: Clock) : TokenProvider {
    init {
        require(secretBytes(properties.accessSecret).size >= MIN_SECRET_BYTES) {
            "JWT access secret must be at least $MIN_SECRET_BYTES bytes"
        }
        require(secretBytes(properties.refreshSecret).size >= MIN_SECRET_BYTES) {
            "JWT refresh secret must be at least $MIN_SECRET_BYTES bytes"
        }
    }

    override fun issueAccessToken(memberId: Long): String = issue(
        subject = memberId.toString(),
        type = ACCESS_TYPE,
        expiresAt = Instant.now(clock).plus(properties.accessTtl),
        secret = properties.accessSecret,
    )

    override fun issueRefreshToken(memberId: Long): IssuedToken {
        val identifier = UUID.randomUUID().toString()
        val expiresAt = Instant.now(clock).plus(properties.refreshTtl)
        return IssuedToken(
            value = issue(
                subject = memberId.toString(),
                type = REFRESH_TYPE,
                expiresAt = expiresAt,
                secret = properties.refreshSecret,
                identifier = identifier,
            ),
            identifier = identifier,
            expiresAt = expiresAt,
        )
    }

    override fun parseRefreshToken(token: String): RefreshClaims {
        val claims = parse(token, properties.refreshSecret, REFRESH_TYPE) { InvalidRefreshTokenException() }
        return runCatching {
            RefreshClaims(
                memberId = claims.subject.toLong(),
                tokenIdentifier = requireNotNull(claims.jwtid),
            )
        }.getOrElse { throw InvalidRefreshTokenException() }
    }

    override fun hash(token: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8)),
    )

    private fun issue(
        subject: String,
        type: String,
        expiresAt: Instant,
        secret: String,
        identifier: String = UUID.randomUUID().toString(),
        claims: Map<String, String> = emptyMap(),
    ): String {
        val now = Instant.now(clock)
        val builder = JWTClaimsSet.Builder()
            .issuer(properties.issuer)
            .subject(subject)
            .jwtID(identifier)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(expiresAt))
            .claim(TOKEN_TYPE, type)
        claims.forEach(builder::claim)
        return SignedJWT(JWSHeader(JWSAlgorithm.HS256), builder.build()).apply {
            sign(MACSigner(secretBytes(secret)))
        }.serialize()
    }

    private fun parse(
        token: String,
        secret: String,
        expectedType: String,
        exception: () -> RuntimeException,
    ): JWTClaimsSet = runCatching {
        val jwt = SignedJWT.parse(token)
        val claims = jwt.jwtClaimsSet
        check(jwt.verify(MACVerifier(secretBytes(secret))))
        check(claims.issuer == properties.issuer)
        check(claims.getStringClaim(TOKEN_TYPE) == expectedType)
        check(claims.expirationTime.after(Date.from(Instant.now(clock))))
        claims
    }.getOrElse { throw exception() }

    private fun secretBytes(secret: String): ByteArray = secret.toByteArray(StandardCharsets.UTF_8)
}
