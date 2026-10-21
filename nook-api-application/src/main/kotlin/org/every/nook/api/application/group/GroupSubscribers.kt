package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupReadAccessPort
import org.every.nook.api.application.group.port.GroupSharePort
import org.every.nook.api.application.group.port.GroupSubscribersPort

data class GroupSubscriberView(val nickname: String, val profileImageUrl: String?)

data class GroupSubscriberSummary(val count: Long, val preview: List<GroupSubscriberView>)

data class GroupSubscriberPage(
    val items: List<GroupSubscriberView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

class ListGroupSubscribersUseCase(
    private val accessPort: GroupReadAccessPort,
    private val subscribersPort: GroupSubscribersPort,
) {
    operator fun invoke(query: Query): GroupSubscriberPage {
        accessPort.resolveOwnerId(query.userId, query.groupId) ?: throw GroupNotFoundException()
        return subscribersPort.findPage(query.groupId, query.page, query.size)
    }

    data class Query(val userId: Long, val groupId: Long, val page: Int, val size: Int)
}

class ListSharedGroupSubscribersUseCase(
    private val sharePort: GroupSharePort,
    private val subscribersPort: GroupSubscribersPort,
) {
    operator fun invoke(token: String, page: Int, size: Int): GroupSubscriberPage {
        val access = sharePort.resolveActive(token)
        return subscribersPort.findPage(access.groupId, page, size)
    }
}

internal fun GroupView.withSubscribers(summary: GroupSubscriberSummary?): GroupView = copy(
    subscriberCount = summary?.count ?: 0,
    subscriberPreview = summary?.preview.orEmpty(),
)

internal fun List<GroupView>.withSubscribers(port: GroupSubscribersPort): List<GroupView> {
    if (isEmpty()) return this
    val summaries = port.findSummaries(mapTo(mutableSetOf(), GroupView::id))
    return map { it.withSubscribers(summaries[it.id]) }
}
