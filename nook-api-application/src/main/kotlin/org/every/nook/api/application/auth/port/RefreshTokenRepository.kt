package org.every.nook.api.application.auth.port

import java.time.Instant

data class StoredRefreshToken(
    val id: Long? = null,
    val memberId: Long,
    val tokenIdentifier: String,
    val tokenHash: String,
    val expiresAt: Instant,
    val revokedAt: Instant? = null,
    val replacedByTokenId: Long? = null,
)

interface RefreshTokenRepository {
    fun save(token: StoredRefreshToken): StoredRefreshToken

    fun findByIdentifierForUpdate(identifier: String): StoredRefreshToken?

    fun revoke(tokenId: Long, revokedAt: Instant, replacedByTokenId: Long)

    fun revokeActiveTokens(memberId: Long, revokedAt: Instant)
}
