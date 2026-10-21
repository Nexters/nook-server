package org.every.nook.api.infrastructure.persistence.group

import org.every.nook.api.application.group.GroupSubscriberPage
import org.every.nook.api.application.group.GroupSubscriberSummary
import org.every.nook.api.application.group.GroupSubscriberView
import org.every.nook.api.application.group.port.GroupSubscribersPort
import org.every.nook.api.infrastructure.persistence.group.subscriber.GroupSubscriberJpaRepository
import org.every.nook.api.infrastructure.persistence.group.subscriber.GroupSubscriberProjection
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class GroupSubscriberQueryAdapter(
    private val repository: GroupSubscriberJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : GroupSubscribersPort {
    @Transactional(readOnly = true)
    override fun findSummaries(groupIds: Set<Long>): Map<Long, GroupSubscriberSummary> {
        if (groupIds.isEmpty()) return emptyMap()
        return repository.findSummaries(groupIds, clock.instant()).groupBy { it.groupId }.mapValues { (_, rows) ->
            GroupSubscriberSummary(rows.first().subscriberCount, rows.map { it.toView() })
        }
    }

    @Transactional(readOnly = true)
    override fun findPage(groupId: Long, page: Int, size: Int): GroupSubscriberPage {
        val result = repository.findSubscribers(groupId, clock.instant(), PageRequest.of(page, size))
        return GroupSubscriberPage(
            items = result.content.map { it.toView() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            hasNext = result.hasNext(),
        )
    }

    private fun GroupSubscriberProjection.toView(): GroupSubscriberView = GroupSubscriberView(nickname, profileImageUrl)
}
