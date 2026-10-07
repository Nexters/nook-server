package org.every.nook.api.infrastructure.persistence.onboarding

import org.every.nook.api.application.onboarding.OnboardingConversionQueryPort
import org.every.nook.api.application.onboarding.OnboardingEvent
import org.every.nook.api.application.onboarding.OnboardingEventStorePort
import org.every.nook.api.application.onboarding.OnboardingJourney
import org.every.nook.api.application.onboarding.OnboardingPeriod
import org.every.nook.api.application.onboarding.OnboardingSave
import org.every.nook.api.infrastructure.persistence.analytics.UserAnalyticsEventJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Component
class OnboardingPersistenceAdapter(
    private val repository: OnboardingEventJpaRepository,
    private val analytics: UserAnalyticsEventJpaRepository,
) : OnboardingEventStorePort,
    OnboardingConversionQueryPort {
    @Transactional
    override fun store(event: OnboardingEvent) {
        repository.insertIdempotently(event.memberId, event.eventId.toString(), event.eventName.name, event.occurredAt)
        val stored = repository.findByMemberIdAndEventId(event.memberId, event.eventId.toString())
        require(stored.eventName == event.eventName && stored.occurredAt == event.occurredAt) {
            "An event ID cannot be reused with a different payload"
        }
    }

    @Transactional(readOnly = true)
    override fun journeys(period: OnboardingPeriod, observedAt: Instant): List<OnboardingJourney> =
        repository.cohort(period.fromInstant, period.toExclusive, observedAt).chunked(BATCH_SIZE).flatMap { cohort ->
            val members = cohort.map { it.memberId }
            val from = cohort.minOf { it.clickedAt }
            val to = minOf(cohort.maxOf { it.clickedAt }.plus(Duration.ofDays(1)), observedAt)
            val saves = analytics.onboardingSaves(members, from, to).groupBy { it.memberId }
            val entries = repository.extensionEntries(members, from, to).groupBy { it.memberId }
            cohort.map { member ->
                OnboardingJourney(
                    clickedAt = member.clickedAt,
                    saves = saves[member.memberId].orEmpty().mapNotNull { event ->
                        event.targetId?.let { OnboardingSave(it, event.occurredAt) }
                    },
                    extensionEntries = entries[member.memberId].orEmpty().map { it.occurredAt },
                )
            }
        }

    private companion object {
        const val BATCH_SIZE = 500
    }
}
