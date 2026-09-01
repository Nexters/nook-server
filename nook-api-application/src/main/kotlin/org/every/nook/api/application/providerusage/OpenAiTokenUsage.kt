package org.every.nook.api.application.providerusage

import java.time.Instant
import java.time.LocalDate

data class OpenAiTokenUsage(
    val feature: String,
    val model: String,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val occurredAt: Instant,
)

fun interface OpenAiTokenUsageRecorder {
    fun record(usage: OpenAiTokenUsage)
}

data class OpenAiTokenUsagePeriod(val start: LocalDate, val end: LocalDate)

data class OpenAiTokenUsageOverview(
    val from: LocalDate,
    val to: LocalDate,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val daily: List<Daily>,
    val breakdowns: List<Breakdown>,
) {
    data class Daily(
        val date: LocalDate,
        val inputTokens: Long,
        val cachedInputTokens: Long,
        val outputTokens: Long,
        val totalTokens: Long,
    )

    data class Breakdown(
        val feature: String,
        val model: String,
        val requests: Long,
        val inputTokens: Long,
        val cachedInputTokens: Long,
        val outputTokens: Long,
        val totalTokens: Long,
    )
}

fun interface OpenAiTokenUsageQueryPort {
    fun get(period: OpenAiTokenUsagePeriod): OpenAiTokenUsageOverview
}

class GetOpenAiTokenUsageUseCase(private val port: OpenAiTokenUsageQueryPort) {
    operator fun invoke(period: OpenAiTokenUsagePeriod) = port.get(period)
}
