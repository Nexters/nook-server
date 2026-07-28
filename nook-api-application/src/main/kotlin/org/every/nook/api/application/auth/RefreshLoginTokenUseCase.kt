package org.every.nook.api.application.auth

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.auth.port.StoredRefreshToken
import org.every.nook.api.application.auth.port.TokenProvider
import org.every.nook.api.application.member.port.MemberRepository
import org.every.nook.api.application.port.TransactionRunner
import java.time.Clock
import java.time.Instant

class RefreshLoginTokenUseCase(
    private val tokenProvider: TokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberRepository: MemberRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    operator fun invoke(refreshToken: String): LoginTokens {
        val claims = tokenProvider.parseRefreshToken(refreshToken)
        val result = transactionRunner.required {
            rotate(refreshToken, claims.memberId, claims.tokenIdentifier)
        }
        if (result is RefreshResult.Reused) throw ReusedRefreshTokenException()
        return (result as RefreshResult.Success).tokens
    }

    private fun rotate(rawToken: String, memberId: Long, tokenIdentifier: String): RefreshResult {
        val stored = refreshTokenRepository.findByIdentifierForUpdate(tokenIdentifier)
            ?: throw InvalidRefreshTokenException()
        val now = Instant.now(clock)
        if (stored.revokedAt != null) {
            refreshTokenRepository.revokeActiveTokens(memberId, now)
            return RefreshResult.Reused
        }
        validateStoredToken(stored, rawToken, memberId, now)
        if (!memberRepository.existsMember(memberId)) throw InvalidRefreshTokenException()

        val replacement = tokenProvider.issueRefreshToken(memberId)
        val savedReplacement = refreshTokenRepository.save(
            StoredRefreshToken(
                memberId = memberId,
                tokenIdentifier = replacement.identifier,
                tokenHash = tokenProvider.hash(replacement.value),
                expiresAt = replacement.expiresAt,
            ),
        )
        refreshTokenRepository.revoke(requireNotNull(stored.id), now, requireNotNull(savedReplacement.id))
        return RefreshResult.Success(
            LoginTokens(
                accessToken = tokenProvider.issueAccessToken(memberId),
                refreshToken = replacement.value,
            ),
        )
    }

    private fun validateStoredToken(stored: StoredRefreshToken, rawToken: String, memberId: Long, now: Instant) {
        val mismatched = stored.memberId != memberId ||
            stored.expiresAt <= now ||
            stored.tokenHash != tokenProvider.hash(rawToken)
        if (mismatched) {
            throw InvalidRefreshTokenException()
        }
    }

    private sealed interface RefreshResult {
        data class Success(val tokens: LoginTokens) : RefreshResult

        data object Reused : RefreshResult
    }
}
