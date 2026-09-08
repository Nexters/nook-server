package org.every.nook.api.infrastructure.persistence.analytics

import org.every.nook.api.application.analytics.AnalyticsCoverage
import org.every.nook.api.application.analytics.UserAnalyticsEvent
import org.every.nook.api.application.analytics.UserAnalyticsEventQueryPort
import org.every.nook.api.application.analytics.UserAnalyticsEventStorePort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class UserAnalyticsEventPersistenceAdapter(private val repository: UserAnalyticsEventJpaRepository) :
    UserAnalyticsEventStorePort,
    UserAnalyticsEventQueryPort {
    @Transactional(readOnly = true)
    override fun coverage(): AnalyticsCoverage = repository.coverage().let {
        AnalyticsCoverage(it.firstEventAt, it.lastEventAt)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun store(event: UserAnalyticsEvent) {
        repository.insertIgnore(
            eventId = event.eventId,
            deduplicationKey = event.deduplicationKey,
            eventName = event.eventName.name,
            memberId = event.memberId,
            targetType = event.targetType?.name,
            targetId = event.targetId,
            occurredAt = event.occurredAt,
        )
    }

    @Transactional(readOnly = true)
    override fun findAll(from: Instant, to: Instant): List<UserAnalyticsEvent> =
        repository.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(from, to)
            .map { entity ->
                UserAnalyticsEvent(
                    eventId = entity.eventId,
                    deduplicationKey = entity.deduplicationKey,
                    eventName = entity.eventName,
                    memberId = entity.memberId,
                    targetType = entity.targetType,
                    targetId = entity.targetId,
                    occurredAt = entity.occurredAt,
                )
            }
}
