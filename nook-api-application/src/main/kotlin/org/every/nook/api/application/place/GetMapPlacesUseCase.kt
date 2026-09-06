package org.every.nook.api.application.place

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsRecord
import org.every.nook.api.application.place.port.PlaceMapQueryPort
import org.every.nook.api.domain.place.GeoBounds
import java.math.BigDecimal

class GetMapPlacesUseCase(
    private val queryPort: PlaceMapQueryPort,
    private val analyticsRecorder: UserAnalyticsEventRecorder = UserAnalyticsEventRecorder.NONE,
) {
    operator fun invoke(query: Query): List<MapPlaceView> {
        val result = queryPort.findInBounds(
            userId = query.userId,
            bounds = GeoBounds(
                northLatitude = query.northLatitude,
                westLongitude = query.westLongitude,
                southLatitude = query.southLatitude,
                eastLongitude = query.eastLongitude,
            ),
        )
        analyticsRecorder.record(
            UserAnalyticsRecord(UserAnalyticsEventName.MAP_VIEW, query.userId),
        )
        return result
    }

    data class Query(
        val userId: Long,
        val northLatitude: BigDecimal,
        val westLongitude: BigDecimal,
        val southLatitude: BigDecimal,
        val eastLongitude: BigDecimal,
    )
}
