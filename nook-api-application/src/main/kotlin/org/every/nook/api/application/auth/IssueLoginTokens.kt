package org.every.nook.api.application.auth

import org.every.nook.api.application.auth.port.RefreshTokenRepository
import org.every.nook.api.application.auth.port.StoredRefreshToken
import org.every.nook.api.application.auth.port.TokenProvider

class IssueLoginTokens(
    private val tokenProvider: TokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    operator fun invoke(memberId: Long): LoginTokens {
        val refreshToken = tokenProvider.issueRefreshToken(memberId)
        refreshTokenRepository.save(
            StoredRefreshToken(
                memberId = memberId,
                tokenIdentifier = refreshToken.identifier,
                tokenHash = tokenProvider.hash(refreshToken.value),
                expiresAt = refreshToken.expiresAt,
            ),
        )
        return LoginTokens(
            accessToken = tokenProvider.issueAccessToken(memberId),
            refreshToken = refreshToken.value,
        )
    }
}
