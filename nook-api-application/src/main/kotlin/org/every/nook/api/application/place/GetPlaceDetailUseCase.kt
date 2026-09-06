package org.every.nook.api.application.place

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsRecord
import org.every.nook.api.application.analytics.UserAnalyticsTargetType
import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.PlaceDetailQueryPort

class GetPlaceDetailUseCase(
    private val placeDetailQueryPort: PlaceDetailQueryPort,
    private val analyticsRecorder: UserAnalyticsEventRecorder = UserAnalyticsEventRecorder.NONE,
) {
    operator fun invoke(query: Query): PlaceDetailView {
        val result = placeDetailQueryPort.find(
            userId = query.userId,
            placeId = query.placeId,
            page = query.page,
            size = query.size,
        ) ?: throw PlaceNotFoundException()
        analyticsRecorder.record(
            UserAnalyticsRecord(
                eventName = UserAnalyticsEventName.PLACE_VIEW,
                memberId = query.userId,
                targetType = UserAnalyticsTargetType.PLACE,
                targetId = query.placeId,
            ),
        )
        return result
    }

    data class Query(val userId: Long, val placeId: Long, val page: Int, val size: Int)
}
