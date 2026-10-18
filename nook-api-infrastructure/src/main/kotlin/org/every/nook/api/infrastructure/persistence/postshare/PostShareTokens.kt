package org.every.nook.api.infrastructure.persistence.postshare

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object PostShareTokens {
    private const val TOKEN_BYTES = 32
    private val random = SecureRandom()
    private val pattern = Regex("[A-Za-z0-9_-]{43}")

    fun issue(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        ByteArray(TOKEN_BYTES).also(random::nextBytes),
    )

    fun valid(token: String): Boolean = pattern.matches(token)

    fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
