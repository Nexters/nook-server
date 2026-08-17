package org.every.nook.api.application.error

enum class ErrorType {
    INVALID_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    GONE,
    CONFLICT,
    BAD_GATEWAY,
    GATEWAY_TIMEOUT,
    INTERNAL_ERROR,
}
