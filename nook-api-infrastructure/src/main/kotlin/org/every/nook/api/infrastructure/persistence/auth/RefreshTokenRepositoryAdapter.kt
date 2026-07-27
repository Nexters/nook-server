package org.every.nook.api.infrastructure.persistence.auth

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.auth.port.StoredRefreshToken
import org.every.nook.api.infrastructure.persistence.member.MemberJpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class RefreshTokenRepositoryAdapter(
    private val refreshTokenJpaRepository: RefreshTokenJpaRepository,
    private val memberJpaRepository: MemberJpaRepository,
) : RefreshTokenRepository {
    override fun save(token: StoredRefreshToken): StoredRefreshToken = refreshTokenJpaRepository.save(
        RefreshTokenEntity(
            member = memberJpaRepository.getReferenceById(token.memberId),
            tokenIdentifier = token.tokenIdentifier,
            tokenHash = token.tokenHash,
            expiresAt = token.expiresAt,
            revokedAt = token.revokedAt,
        ),
    ).toStored()

    override fun findByIdentifierForUpdate(identifier: String): StoredRefreshToken? =
        refreshTokenJpaRepository.findByIdentifierForUpdate(identifier)?.toStored()

    override fun revoke(tokenId: Long, revokedAt: Instant, replacedByTokenId: Long) {
        val token = refreshTokenJpaRepository.getReferenceById(tokenId)
        token.revokedAt = revokedAt
        token.replacedByToken = refreshTokenJpaRepository.getReferenceById(replacedByTokenId)
    }

    override fun revokeActiveTokens(memberId: Long, revokedAt: Instant) {
        refreshTokenJpaRepository.revokeActiveTokens(memberId, revokedAt)
    }
}
