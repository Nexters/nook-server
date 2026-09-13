package org.every.nook.api.infrastructure.persistence.processing

import java.security.MessageDigest
import java.time.Instant

internal data class ParsingSummaryJob(
    val type: String,
    val id: Long,
    val status: String,
    val attempt: Int,
    val stage: String,
    val reason: String?,
    val lastFailedAt: Instant?,
)

internal data class ParsingSummary(
    val fingerprint: String,
    val completed: Int,
    val failed: Int,
    val lastFailedAt: Instant,
    val failures: List<Failure>,
) {
    data class Failure(val stage: String, val reason: String, val count: Int)

    companion object {
        fun from(jobs: List<ParsingSummaryJob>): ParsingSummary? {
            if (jobs.any { it.status !in setOf("COMPLETED", "FAILED") }) return null
            val failed = jobs.filter { it.status == "FAILED" }
            val failedAt = failed.mapNotNull { it.lastFailedAt }.maxOrNull() ?: return null
            val state = jobs.sortedWith(compareBy({ it.type }, { it.id })).joinToString("|") {
                "${it.type}:${it.id}:${it.status}:${it.attempt}:${it.lastFailedAt}"
            }
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(state.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val failures = failed.groupingBy { it.stage to safeFailureReason(it.reason) }.eachCount()
                .map { (key, count) -> Failure(key.first, key.second, count) }
            return ParsingSummary(fingerprint, jobs.size - failed.size, failed.size, failedAt, failures)
        }
    }
}

/** Only allowlisted summaries leave the service: provider errors can contain URLs, tokens and bodies. */
internal fun safeFailureReason(reason: String?): String {
    val text = reason.orEmpty().lowercase()
    return when {
        listOf("429", "rate_limit", "rate limit").any { it in text } -> "외부 서비스 호출 제한"
        "timeout" in text || "timed out" in text -> "외부 서비스 응답 시간 초과"
        listOf("401", "403", "configuration").any { it in text } -> "외부 서비스 인증·접근 또는 설정 오류"
        "404" in text || "not found" in text -> "요청한 원본 또는 리소스를 찾을 수 없음"
        "download" in text -> "원본 이미지·미디어 다운로드 실패"
        "no place" in text || "place clue" in text || "not grounded" in text -> "장소 단서에 맞는 장소를 찾지 못함"
        "ocr" in text -> "이미지 문자 인식 실패"
        else -> "처리 오류 — 상세 사유는 관리자 화면에서 확인"
    }
}
