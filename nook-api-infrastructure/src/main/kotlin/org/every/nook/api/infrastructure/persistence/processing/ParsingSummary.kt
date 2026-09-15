package org.every.nook.api.infrastructure.persistence.processing

import org.every.nook.api.application.processing.processingFailureDetail
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
    val retryAttempt: Int = attempt,
    val noPlaces: Boolean = false,
)

internal data class ParsingSummary(
    val fingerprint: String,
    val completed: Int,
    val failed: Int,
    val lastFailedAt: Instant?,
    val noPlaces: Boolean,
    val failures: List<Failure>,
) {
    data class Failure(
        val stage: String,
        val reason: String,
        val count: Int,
        val detail: String,
        val code: String?,
        val attempts: Int,
        val retried: Boolean,
    )

    companion object {
        fun from(jobs: List<ParsingSummaryJob>): ParsingSummary? {
            if (jobs.any { it.status !in setOf("COMPLETED", "FAILED") }) return null
            val failed = jobs.filter { it.status == "FAILED" }
            val noPlaces = jobs.any { it.type == "PLACE_PARSING" && it.status == "COMPLETED" && it.noPlaces }
            val failedAt = failed.mapNotNull { it.lastFailedAt }.maxOrNull()
            if (failedAt == null && !noPlaces) return null
            val state = jobs.sortedWith(compareBy({ it.type }, { it.id })).joinToString("|") {
                "${it.type}:${it.id}:${it.status}:${it.attempt}:${it.lastFailedAt}"
            } + if (noPlaces) "|NO_PLACES" else ""
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(state.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val failures = failed.groupBy { it.stage to processingFailureDetail(it.reason ?: "실패 사유 미기록") }
                .map { (key, jobs) ->
                    Failure(
                        key.first,
                        key.second.summary,
                        jobs.size,
                        key.second.detail,
                        key.second.code,
                        jobs.maxOf { it.attempt },
                        jobs.any { it.attempt > it.retryAttempt },
                    )
                }
            return ParsingSummary(fingerprint, jobs.size - failed.size, failed.size, failedAt, noPlaces, failures)
        }
    }
}
