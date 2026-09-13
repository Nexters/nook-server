package org.every.nook.api.application.processing

import java.time.Duration

enum class ParsingFailureKind(val retryable: Boolean) {
    TRANSIENT(true),
    RATE_LIMIT(true),
    PERMANENT(false),
    CONFIGURATION(false),
    UNKNOWN(true),
}

data class ParsingFailure(val kind: ParsingFailureKind, val reason: String, val retryAfter: Duration? = null) {
    fun storedReason(): String = "[${kind.name}] $reason"
}

fun interface ParsingFailureClassifier {
    fun classify(cause: Throwable): ParsingFailure

    companion object {
        val DEFAULT = ParsingFailureClassifier { cause ->
            ParsingFailure(ParsingFailureKind.UNKNOWN, cause.message.orEmpty().ifBlank { "Parsing failed" })
        }
    }
}
