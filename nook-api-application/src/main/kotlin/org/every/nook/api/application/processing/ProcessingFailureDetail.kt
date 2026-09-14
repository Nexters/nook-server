package org.every.nook.api.application.processing

data class ProcessingFailureDetail(val summary: String, val detail: String, val code: String?)

fun processingFailureDetail(reason: String): ProcessingFailureDetail {
    val safe = redactProcessingText(reason)
    val text = safe.lowercase()
    val code = Regex("(?i)(?:http(?: status)?|status(?: code)?|statusCode)[:= ]+(4[0-9]{2}|5[0-9]{2})")
        .find(safe)?.groupValues?.get(1)
    val summary = FAILURE_PATTERNS.firstOrNull { (pattern, _) -> pattern.containsMatchIn(text) }?.second
        ?: "처리 중 오류가 발생했습니다"
    return ProcessingFailureDetail(summary, safe.take(MAX_DETAIL_LENGTH), code)
}

fun redactProcessingText(value: String): String = value
    .replace(Regex("https?://[^\\s<>\"]+", RegexOption.IGNORE_CASE), "[원본 URL 숨김]")
    .replace(Regex("(?i)(bearer\\s+)[a-z0-9._~+/-]+"), "$1[숨김]")
    .replace(SECRET_VALUE, "$1[숨김]")

private const val MAX_DETAIL_LENGTH = 400
private val FAILURE_PATTERNS = listOf(
    Regex("429|rate.limit") to "외부 서비스 호출 한도를 초과했습니다",
    Regex("timeout|timed out") to "응답 제한 시간을 초과했습니다",
    Regex("401|403|configuration|unauthorized|forbidden") to "인증·접근 권한 또는 서비스 설정을 확인해야 합니다",
    Regex("404|not found") to "요청한 원본 또는 리소스를 찾을 수 없습니다",
    Regex("download") to "원본 이미지·미디어를 내려받지 못했습니다",
    Regex("no place|place clue|not grounded") to "장소 단서에 맞는 장소를 찾지 못했습니다",
    Regex("ocr") to "이미지의 문자를 읽지 못했습니다",
    Regex("connection|502|503|504") to "서비스 연결 또는 응답에 문제가 있습니다",
    Regex("json|deserializ|parse|invalid.*response") to "응답 데이터 형식을 해석하지 못했습니다",
)

private val SECRET_VALUE = Regex(
    "(?i)([\"]?(?:token|password|secret|api[_-]?key|authorization)[\"]?\\s*[:=]\\s*)" +
        "(?:\"[^\"]*\"|'[^']*'|[^,;\\s]+)",
)
