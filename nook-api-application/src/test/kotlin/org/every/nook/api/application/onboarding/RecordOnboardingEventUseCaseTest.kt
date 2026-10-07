package org.every.nook.api.application.onboarding

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecordOnboardingEventUseCaseTest {
    private val now = Instant.parse("2026-10-07T14:00:00Z")
    private val event = OnboardingEvent(7, UUID.randomUUID(), OnboardingEventName.INSTAGRAM_CTA_CLICK, now)

    @Test
    fun `keeps event identity and actual time on delayed retry at database precision`() {
        val stored = mutableListOf<OnboardingEvent>()
        val useCase = RecordOnboardingEventUseCase(
            OnboardingEventStorePort(stored::add),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        val delayed = event.copy(occurredAt = now.minusSeconds(3600).plusNanos(123456789))
        repeat(2) { useCase(delayed) }
        assertEquals(stored.first(), stored.last())
        assertEquals(event.eventId, stored.first().eventId)
        assertEquals(now.minusSeconds(3600).plusNanos(123456000), stored.first().occurredAt)
    }

    @Test
    fun `rejects invalid user future and stale events before persistence`() {
        val useCase = RecordOnboardingEventUseCase(OnboardingEventStorePort { error("Must not write") }, clock())
        assertFailsWith<IllegalArgumentException> { useCase(event.copy(memberId = 0)) }
        assertFailsWith<IllegalArgumentException> { useCase(event.copy(occurredAt = now.plusNanos(1))) }
        assertFailsWith<IllegalArgumentException> { useCase(event.copy(occurredAt = now.minusSeconds(604801))) }
    }

    @Test
    fun `storage failure propagates for retry instead of acknowledging a lost event`() {
        val useCase = RecordOnboardingEventUseCase(OnboardingEventStorePort { error("database unavailable") }, clock())
        assertFailsWith<IllegalStateException> { useCase(event) }
    }

    private fun clock() = Clock.fixed(now, ZoneOffset.UTC)
}
