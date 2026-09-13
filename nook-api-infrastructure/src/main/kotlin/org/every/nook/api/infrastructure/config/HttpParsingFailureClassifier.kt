package org.every.nook.api.infrastructure.config

import org.every.nook.api.application.processing.ParsingFailure
import org.every.nook.api.application.processing.ParsingFailureClassifier
import org.every.nook.api.application.processing.ParsingFailureKind
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientResponseException
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Component
class HttpParsingFailureClassifier(private val clock: Clock = Clock.systemUTC()) : ParsingFailureClassifier {
    override fun classify(cause: Throwable): ParsingFailure {
        val chain = generateSequence(cause) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        val response = chain.filterIsInstance<RestClientResponseException>().firstOrNull()
        return when {
            response?.statusCode?.value() == RATE_LIMIT_STATUS -> ParsingFailure(
                ParsingFailureKind.RATE_LIMIT,
                "외부 서비스 요청 한도 초과",
                retryAfter(response),
            )

            response?.statusCode?.value() in CONFIGURATION_STATUSES ->
                ParsingFailure(ParsingFailureKind.CONFIGURATION, "외부 서비스 인증 또는 권한 확인 필요")

            response?.statusCode?.value() in PERMANENT_STATUSES ->
                ParsingFailure(ParsingFailureKind.PERMANENT, "외부 서비스에서 원본을 찾을 수 없거나 요청을 처리할 수 없음")

            response?.statusCode?.is5xxServerError == true || chain.any { it is IOException } ->
                ParsingFailure(ParsingFailureKind.TRANSIENT, "외부 서비스 또는 네트워크 일시 오류")

            else -> ParsingFailure(ParsingFailureKind.UNKNOWN, "파싱 작업 실패: 실행 로그 확인 필요")
        }
    }

    private fun retryAfter(response: RestClientResponseException): Duration? {
        val value = response.responseHeaders?.getFirst("Retry-After") ?: return null
        val seconds = value.toLongOrNull()?.coerceIn(0, MAX_RETRY_SECONDS)
        if (seconds != null) return Duration.ofSeconds(seconds)
        return runCatching {
            Duration.between(
                clock.instant(),
                ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
            )
                .coerceIn(Duration.ZERO, Duration.ofSeconds(MAX_RETRY_SECONDS))
        }.getOrNull()
    }

    private companion object {
        const val MAX_CAUSE_DEPTH = 10
        const val RATE_LIMIT_STATUS = 429
        const val MAX_RETRY_SECONDS = 1800L
        val CONFIGURATION_STATUSES = setOf(401, 403)
        val PERMANENT_STATUSES = setOf(400, 404, 410, 422)
    }
}
