package org.every.nook.api.infrastructure.persistence.providerusage

import org.every.nook.api.application.providerusage.OpenAiTokenUsage
import org.every.nook.api.application.providerusage.OpenAiTokenUsageOverview
import org.every.nook.api.application.providerusage.OpenAiTokenUsagePeriod
import org.every.nook.api.application.providerusage.OpenAiTokenUsageQueryPort
import org.every.nook.api.application.providerusage.OpenAiTokenUsageRecorder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@Component
class OpenAiTokenUsagePersistenceAdapter(private val repository: OpenAiTokenUsageJpaRepository) :
    OpenAiTokenUsageRecorder,
    OpenAiTokenUsageQueryPort {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun record(usage: OpenAiTokenUsage) {
        repository.save(
            OpenAiTokenUsageEntity(
                feature = usage.feature,
                model = usage.model,
                inputTokens = usage.inputTokens,
                cachedInputTokens = usage.cachedInputTokens,
                outputTokens = usage.outputTokens,
                totalTokens = usage.totalTokens,
                occurredAt = usage.occurredAt,
            ),
        )
    }

    @Transactional(readOnly = true)
    override fun get(period: OpenAiTokenUsagePeriod): OpenAiTokenUsageOverview {
        val rows = repository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            period.start.atStartOfDay(ADMIN_ZONE).toInstant(),
            period.end.atStartOfDay(ADMIN_ZONE).toInstant(),
        )
        return OpenAiTokenUsageOverview(
            from = period.start,
            to = period.end,
            inputTokens = rows.sumOf { it.inputTokens },
            cachedInputTokens = rows.sumOf { it.cachedInputTokens },
            outputTokens = rows.sumOf { it.outputTokens },
            totalTokens = rows.sumOf { it.totalTokens },
            daily = rows.groupBy { it.occurredAt.atZone(ADMIN_ZONE).toLocalDate() }
                .map { (date, events) -> events.toDaily(date) }
                .sortedBy { it.date },
            breakdowns = rows.groupBy { it.feature to it.model }
                .map { (key, events) -> events.toBreakdown(key.first, key.second) }
                .sortedByDescending { it.totalTokens },
        )
    }

    private fun List<OpenAiTokenUsageEntity>.toDaily(date: LocalDate) = OpenAiTokenUsageOverview.Daily(
        date = date,
        inputTokens = sumOf { it.inputTokens },
        cachedInputTokens = sumOf { it.cachedInputTokens },
        outputTokens = sumOf { it.outputTokens },
        totalTokens = sumOf { it.totalTokens },
    )

    private fun List<OpenAiTokenUsageEntity>.toBreakdown(feature: String, model: String) =
        OpenAiTokenUsageOverview.Breakdown(
            feature = feature,
            model = model,
            requests = size.toLong(),
            inputTokens = sumOf { it.inputTokens },
            cachedInputTokens = sumOf { it.cachedInputTokens },
            outputTokens = sumOf { it.outputTokens },
            totalTokens = sumOf { it.totalTokens },
        )

    private companion object {
        val ADMIN_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
