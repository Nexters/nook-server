package org.every.nook.api.application.auth.port

import org.every.nook.api.application.auth.IssuedToken
import org.every.nook.api.application.auth.RefreshClaims
import org.every.nook.api.application.auth.SignupClaims
import org.every.nook.api.domain.member.SocialProvider

interface TokenProvider {
    fun issueAccessToken(memberId: Long): String

    fun issueRefreshToken(memberId: Long): IssuedToken

    fun issueSignupToken(provider: SocialProvider, subject: String): String

    fun parseRefreshToken(token: String): RefreshClaims

    fun parseSignupToken(token: String): SignupClaims

    fun hash(token: String): String
}
