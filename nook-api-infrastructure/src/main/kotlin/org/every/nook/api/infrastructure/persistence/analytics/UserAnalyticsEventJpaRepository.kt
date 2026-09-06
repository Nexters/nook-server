package org.every.nook.api.infrastructure.persistence.analytics

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface UserAnalyticsEventJpaRepository : JpaRepository<UserAnalyticsEventEntity, Long> {
    @Modifying
    @Query(
        value = """
            INSERT IGNORE INTO analytics_events (
                event_id,
                deduplication_key,
                event_name,
                member_id,
                target_type,
                target_id,
                occurred_at,
                created_at,
                updated_at
            ) VALUES (
                :eventId,
                :deduplicationKey,
                :eventName,
                :memberId,
                :targetType,
                :targetId,
                :occurredAt,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
        """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("eventId") eventId: String,
        @Param("deduplicationKey") deduplicationKey: String,
        @Param("eventName") eventName: String,
        @Param("memberId") memberId: Long,
        @Param("targetType") targetType: String?,
        @Param("targetId") targetId: Long?,
        @Param("occurredAt") occurredAt: Instant,
    ): Int

    fun findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
        from: Instant,
        to: Instant,
    ): List<UserAnalyticsEventEntity>
}
