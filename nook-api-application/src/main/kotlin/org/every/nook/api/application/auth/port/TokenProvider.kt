package org.every.nook.api.application.auth.port

import org.every.nook.api.application.auth.IssuedToken
import org.every.nook.api.application.auth.RefreshClaims

interface TokenProvider {
    fun issueAccessToken(memberId: Long): String

    fun issueRefreshToken(memberId: Long): IssuedToken

    fun parseRefreshToken(token: String): RefreshClaims

    fun hash(token: String): String
}
