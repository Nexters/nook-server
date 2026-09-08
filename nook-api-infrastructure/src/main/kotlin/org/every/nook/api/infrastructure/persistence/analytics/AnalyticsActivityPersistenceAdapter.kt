package org.every.nook.api.infrastructure.persistence.analytics

import org.every.nook.api.application.analytics.AnalyticsActivityMember
import org.every.nook.api.application.analytics.AnalyticsActivityPage
import org.every.nook.api.application.analytics.AnalyticsActivityQuery
import org.every.nook.api.application.analytics.AnalyticsActivityQueryPort
import org.every.nook.api.application.analytics.UserAnalyticsEvent
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class AnalyticsActivityPersistenceAdapter(private val repository: AnalyticsActivityJpaRepository) :
    AnalyticsActivityQueryPort {
    override fun members(query: AnalyticsActivityQuery): AnalyticsActivityPage<AnalyticsActivityMember> {
        val result = repository.activityMembers(
            query.fromInstant,
            query.toExclusive,
            query.eventName,
            PageRequest.of(query.page, query.size),
        )
        return AnalyticsActivityPage(
            result.content.map { AnalyticsActivityMember(it.memberId, it.nickname, it.events, it.lastEventAt) },
            result.totalElements,
            query.page,
            query.size,
        )
    }

    override fun events(query: AnalyticsActivityQuery, memberId: Long): AnalyticsActivityPage<UserAnalyticsEvent> {
        val result = repository.memberEvents(
            query.fromInstant,
            query.toExclusive,
            memberId,
            query.eventName,
            PageRequest.of(query.page, query.size),
        )
        return AnalyticsActivityPage(
            result.content.map {
                UserAnalyticsEvent(
                    it.eventId,
                    it.deduplicationKey,
                    it.eventName,
                    it.memberId,
                    it.targetType,
                    it.targetId,
                    it.occurredAt,
                )
            },
            result.totalElements,
            query.page,
            query.size,
        )
    }
}
