package org.every.nook.api.logging

object RequestLoggingFields {
    const val REQUEST_ID_HEADER = "X-Request-Id"

    const val REQUEST_ID = "request.id"
    const val REQUEST_METHOD = "request.method"
    const val REQUEST_PATH = "request.path"
    const val REQUEST_QUERY = "request.query"
    const val REQUEST_URL = "request.url"
    const val REQUEST_CLIENT_IP = "request.client.ip"
    const val REQUEST_CONTENT_TYPE = "request.content_type"
    const val REQUEST_SIZE_BYTES = "request.size.bytes"

    const val RESPONSE_STATUS = "response.status"
    const val RESPONSE_CONTENT_TYPE = "response.content_type"
    const val RESPONSE_SIZE_BYTES = "response.size.bytes"

    const val HTTP_METHOD = "http.method"
    const val HTTP_ROUTE = "http.route"
    const val HTTP_STATUS_CODE = "http.status_code"

    const val TRANSACTION_NAME = "transaction.name"
    const val TRANSACTION_TYPE = "transaction.type"
    const val TRANSACTION_DURATION_MS = "transaction.duration.ms"

    const val USER_ID = "user.id"
    const val ERROR_TYPE = "error.type"
    const val ERROR_MESSAGE = "error.message"
}
