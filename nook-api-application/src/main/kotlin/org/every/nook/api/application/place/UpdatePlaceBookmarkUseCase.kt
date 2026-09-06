package org.every.nook.api.application.place

import org.every.nook.api.application.analytics.UserAnalyticsEventName
import org.every.nook.api.application.analytics.UserAnalyticsEventRecorder
import org.every.nook.api.application.analytics.UserAnalyticsRecord
import org.every.nook.api.application.analytics.UserAnalyticsTargetType
import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.UpdatePlaceBookmarkPort

class UpdatePlaceBookmarkUseCase(
    private val updatePlaceBookmarkPort: UpdatePlaceBookmarkPort,
    private val analyticsRecorder: UserAnalyticsEventRecorder = UserAnalyticsEventRecorder.NONE,
) {
    operator fun invoke(command: Command) {
        val updated = updatePlaceBookmarkPort.update(
            userId = command.userId,
            placeId = command.placeId,
            bookmarked = command.bookmarked,
        )
        if (!updated.accessible) {
            throw PlaceNotFoundException()
        }
        if (command.bookmarked && updated.changed) {
            analyticsRecorder.record(
                UserAnalyticsRecord(
                    eventName = UserAnalyticsEventName.PLACE_SAVE,
                    memberId = command.userId,
                    targetType = UserAnalyticsTargetType.PLACE,
                    targetId = command.placeId,
                ),
            )
        }
    }

    data class Command(val userId: Long, val placeId: Long, val bookmarked: Boolean)
}
