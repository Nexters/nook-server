package org.every.nook.api.application.group

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsRecord
import org.every.nook.api.application.analytics.UserAnalyticsTargetType
import org.every.nook.api.application.group.error.GroupNotFoundException
import org.every.nook.api.application.group.port.GroupPlaceQueryPort

class ListGroupPlacesUseCase(
    private val groupPlaceQueryPort: GroupPlaceQueryPort,
    private val groupReadAccessPort: org.every.nook.api.application.group.port.GroupReadAccessPort =
        org.every.nook.api.application.group.port.GroupReadAccessPort { memberId, _ -> memberId },
    private val analyticsRecorder: UserAnalyticsEventRecorder = UserAnalyticsEventRecorder.NONE,
) {
    operator fun invoke(query: Query): GroupPlacePage {
        val result = groupPlaceQueryPort.findPlaces(
            userId = groupReadAccessPort.resolveOwnerId(query.userId, query.groupId) ?: throw GroupNotFoundException(),
            groupId = query.groupId,
            page = query.page,
            size = query.size,
        ) ?: throw GroupNotFoundException()
        if (query.page == 0) {
            analyticsRecorder.record(
                UserAnalyticsRecord(
                    eventName = UserAnalyticsEventName.ARCHIVE_VIEW,
                    memberId = query.userId,
                    targetType = UserAnalyticsTargetType.GROUP,
                    targetId = query.groupId,
                ),
            )
        }
        return result
    }

    data class Query(val userId: Long, val groupId: Long, val page: Int, val size: Int)
}
