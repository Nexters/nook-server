package org.every.nook.api.infrastructure.persistence.analytics

import org.every.nook.api.application.analytics.AnalyticsCoverage
import org.every.nook.api.application.analytics.UserAnalyticsEvent
import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UserAnalyticsEventPersistenceAdapterTest {
    private val repository = mock(UserAnalyticsEventJpaRepository::class.java)
    private val adapter = UserAnalyticsEventPersistenceAdapter(repository)

    @Test
    fun `maps global coverage including empty history`() {
        val projection = mock(AnalyticsCoverageProjection::class.java)
        `when`(repository.coverage()).thenReturn(projection)
        assertEquals(AnalyticsCoverage(null, null), adapter.coverage())
        val first = Instant.parse("2026-09-06T06:30:00Z")
        val last = first.plusSeconds(1)
        `when`(projection.firstEventAt).thenReturn(first)
        `when`(projection.lastEventAt).thenReturn(last)
        assertEquals(AnalyticsCoverage(first, last), adapter.coverage())
    }

    @Test
    fun `stores event with insert ignore for idempotency`() {
        val occurredAt = Instant.parse("2026-09-06T06:30:00Z")
        val event = event(occurredAt)

        adapter.store(event)

        verify(repository).insertIgnore(
            eventId = "event-1",
            deduplicationKey = "dedup-1",
            eventName = "MAP_VIEW",
            memberId = 7,
            targetType = null,
            targetId = null,
            occurredAt = occurredAt,
        )
    }

    @Test
    fun `maps stored entities to application events`() {
        val from = Instant.parse("2026-09-01T00:00:00Z")
        val to = Instant.parse("2026-09-07T00:00:00Z")
        val occurredAt = Instant.parse("2026-09-06T06:30:00Z")
        `when`(
            repository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(from, to),
        ).thenReturn(
            listOf(
                UserAnalyticsEventEntity(
                    eventId = "event-1",
                    deduplicationKey = "dedup-1",
                    eventName = UserAnalyticsEventName.MAP_VIEW,
                    memberId = 7,
                    targetType = null,
                    targetId = null,
                    occurredAt = occurredAt,
                ),
            ),
        )

        assertEquals(listOf(event(occurredAt)), adapter.findAll(from, to))
    }

    private fun event(occurredAt: Instant) = UserAnalyticsEvent(
        eventId = "event-1",
        deduplicationKey = "dedup-1",
        eventName = UserAnalyticsEventName.MAP_VIEW,
        memberId = 7,
        targetType = null,
        targetId = null,
        occurredAt = occurredAt,
    )
}
