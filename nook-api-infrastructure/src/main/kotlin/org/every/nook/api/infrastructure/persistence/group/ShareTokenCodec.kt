package org.every.nook.api.infrastructure.persistence.group

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object ShareTokenCodec {
    private const val TOKEN_BYTES = 32
    private val random = SecureRandom()

    fun generate(): String = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        .let(Base64.getUrlEncoder().withoutPadding()::encodeToString)

    fun hash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
