package org.every.nook.api.infrastructure.persistence.analytics

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import java.time.Instant

interface AnalyticsActivityJpaRepository : Repository<UserAnalyticsEventEntity, Long> {
    @Query(
        value = """
            SELECT e.memberId AS memberId, m.nickname AS nickname,
                   COUNT(e) AS events, MAX(e.occurredAt) AS lastEventAt
            FROM UserAnalyticsEventEntity e
            LEFT JOIN MemberEntity m ON m.id = e.memberId
                AND m.status = org.every.nook.api.domain.member.MemberStatus.ACTIVE
            WHERE e.occurredAt >= :from AND e.occurredAt < :to
              AND (:eventName IS NULL OR e.eventName = :eventName)
            GROUP BY e.memberId, m.nickname
            ORDER BY MAX(e.occurredAt) DESC, e.memberId ASC
        """,
        countQuery = """
            SELECT COUNT(DISTINCT e.memberId) FROM UserAnalyticsEventEntity e
            WHERE e.occurredAt >= :from AND e.occurredAt < :to
              AND (:eventName IS NULL OR e.eventName = :eventName)
        """,
    )
    fun activityMembers(
        from: Instant,
        to: Instant,
        eventName: UserAnalyticsEventName?,
        pageable: Pageable,
    ): Page<AnalyticsMemberProjection>

    @Query(
        """
            SELECT e FROM UserAnalyticsEventEntity e
            WHERE e.occurredAt >= :from AND e.occurredAt < :to AND e.memberId = :memberId
              AND (:eventName IS NULL OR e.eventName = :eventName)
            ORDER BY e.occurredAt DESC, e.eventId DESC
        """,
    )
    fun memberEvents(
        from: Instant,
        to: Instant,
        memberId: Long,
        eventName: UserAnalyticsEventName?,
        pageable: Pageable,
    ): Page<UserAnalyticsEventEntity>
}

interface AnalyticsMemberProjection {
    val memberId: Long
    val nickname: String?
    val events: Long
    val lastEventAt: Instant
}
