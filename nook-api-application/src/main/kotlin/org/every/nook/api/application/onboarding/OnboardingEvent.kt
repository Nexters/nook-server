package org.every.nook.api.application.onboarding

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class OnboardingEventName { INSTAGRAM_CTA_CLICK, SHARE_EXTENSION_OPEN }

data class OnboardingEvent(
    val memberId: Long,
    val eventId: UUID,
    val eventName: OnboardingEventName,
    val occurredAt: Instant,
)

fun interface OnboardingEventStorePort {
    fun store(event: OnboardingEvent)
}

class RecordOnboardingEventUseCase(private val store: OnboardingEventStorePort, private val clock: Clock) {
    operator fun invoke(event: OnboardingEvent) {
        val now = clock.instant()
        require(event.memberId > 0) { "Member ID must be positive" }
        require(event.occurredAt <= now && event.occurredAt >= now.minus(MAX_DELAY)) {
            "Event time must be within the last seven days"
        }
        // Match MySQL TIMESTAMP(6) precision so a retried event compares identically.
        store.store(event.copy(occurredAt = event.occurredAt.truncatedTo(ChronoUnit.MICROS)))
    }

    private companion object {
        val MAX_DELAY: Duration = Duration.ofDays(7)
    }
}
