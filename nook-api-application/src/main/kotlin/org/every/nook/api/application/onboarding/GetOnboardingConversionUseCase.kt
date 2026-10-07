package org.every.nook.api.application.onboarding

import java.time.Clock
import java.time.Duration
import java.time.Instant

class GetOnboardingConversionUseCase(private val query: OnboardingConversionQueryPort, private val clock: Clock) {
    operator fun invoke(period: OnboardingPeriod): OnboardingConversion {
        val now = clock.instant()
        val journeys = query.journeys(period, now).filter { it.clickedAt <= now }.map { it.observed(now) }
        val completed = journeys.filter { it.clickedAt.plus(DAY) <= now }
        return OnboardingConversion(
            period = period,
            observedAt = now,
            clickedUsers = journeys.size,
            windows = WINDOWS.map { minutes -> window(journeys, minutes, now) },
            completedDayUsers = completed.size,
            medianFirstSaveSeconds = median(completed.mapNotNull { it.firstSaveSeconds() }),
            distribution = distribution(completed),
            extension = extension(completed),
        )
    }

    private fun OnboardingJourney.observed(now: Instant): OnboardingJourney {
        val end = minOf(clickedAt.plus(DAY), now)
        return copy(
            saves = saves.filter { it.occurredAt >= clickedAt && it.occurredAt <= end },
            extensionEntries = extensionEntries.filter { it >= clickedAt && it <= end },
        )
    }

    private fun window(journeys: List<OnboardingJourney>, minutes: Long, now: Instant): OnboardingWindow {
        val duration = Duration.ofMinutes(minutes)
        val eligible = journeys.filter { it.clickedAt.plus(duration) <= now }
        fun converted(journey: OnboardingJourney): Boolean = journey.saves.any {
            it.occurredAt <= journey.clickedAt.plus(duration)
        }
        return OnboardingWindow(minutes, journeys.count(::converted), eligible.size, eligible.count(::converted))
    }

    private fun distribution(journeys: List<OnboardingJourney>): List<OnboardingDistribution> {
        val counts = journeys.groupingBy { journey ->
            journey.saves.map(OnboardingSave::postId).distinct().size.coerceAtMost(DISTRIBUTION_CAP)
        }.eachCount()
        return (0..DISTRIBUTION_CAP).map { count ->
            OnboardingDistribution(if (count == DISTRIBUTION_CAP) "3+" else count.toString(), counts[count] ?: 0)
        }
    }

    private fun extension(journeys: List<OnboardingJourney>): OnboardingExtensionFunnel? {
        val entered = journeys.mapNotNull { journey ->
            journey.extensionEntries.minOrNull()?.let { entry -> journey to entry }
        }
        if (entered.isEmpty()) return null
        val saved = entered.mapNotNull { (journey, entry) ->
            journey.saves.filter { it.occurredAt >= entry }.minOfOrNull { it.occurredAt }
                ?.let { seconds(entry, it) }
        }
        return OnboardingExtensionFunnel(
            entered.size,
            saved.size,
            median(entered.map { (journey, entry) -> seconds(journey.clickedAt, entry) }),
            median(saved),
        )
    }

    private fun OnboardingJourney.firstSaveSeconds(): Double? =
        saves.minOfOrNull { it.occurredAt }?.let { seconds(clickedAt, it) }

    private fun seconds(start: Instant, end: Instant): Double =
        Duration.between(start, end).toMillis() / MILLIS_PER_SECOND

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }

    private companion object {
        val DAY: Duration = Duration.ofDays(1)
        val WINDOWS = listOf(5L, 10L, 30L)
        const val DISTRIBUTION_CAP = 3
        const val MILLIS_PER_SECOND = 1000.0
    }
}
