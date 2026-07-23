package org.every.nook.api.application.error

abstract class NookException(
    val errorCode: NookErrorCode,
    val reason: String = errorCode.defaultReason,
    val data: Map<String, Any?>? = null,
    cause: Throwable? = null,
) : RuntimeException(reason, cause)
