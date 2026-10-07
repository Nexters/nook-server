package org.every.nook.api.infrastructure.persistence.onboarding

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OnboardingEventJpaRepository : JpaRepository<OnboardingEventEntity, Long> {
    @Modifying
    @Query(
        value = """
            INSERT INTO onboarding_events (member_id, event_id, event_name, occurred_at, created_at, updated_at)
            VALUES (:memberId, :eventId, :eventName, :occurredAt, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE event_id = event_id
        """,
        nativeQuery = true,
    )
    fun insertIdempotently(
        @Param("memberId") memberId: Long,
        @Param("eventId") eventId: String,
        @Param("eventName") eventName: String,
        @Param("occurredAt") occurredAt: Instant,
    ): Int

    fun findByMemberIdAndEventId(memberId: Long, eventId: String): OnboardingEventEntity

    @Query(
        """
            select e.memberId as memberId, min(e.occurredAt) as clickedAt
            from OnboardingEventEntity e
            where e.eventName = org.every.nook.api.application.onboarding.OnboardingEventName.INSTAGRAM_CTA_CLICK
            group by e.memberId
            having min(e.occurredAt) >= :from and min(e.occurredAt) < :to and min(e.occurredAt) <= :observedAt
        """,
    )
    fun cohort(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("observedAt") observedAt: Instant,
    ): List<OnboardingCohortProjection>

    @Query(
        """
            select e from OnboardingEventEntity e where e.memberId in :memberIds
            and e.eventName = org.every.nook.api.application.onboarding.OnboardingEventName.SHARE_EXTENSION_OPEN
            and e.occurredAt >= :from and e.occurredAt <= :to
        """,
    )
    fun extensionEntries(
        @Param("memberIds") memberIds: List<Long>,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<OnboardingEventEntity>
}

interface OnboardingCohortProjection {
    val memberId: Long
    val clickedAt: Instant
}
