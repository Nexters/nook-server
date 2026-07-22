package org.every.nook.api.application.error

interface NookErrorCode {
    val code: String
    val defaultReason: String
    val type: ErrorType
}
