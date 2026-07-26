package org.every.nook.api.application.place

import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.UpdatePlaceBookmarkPort

class UpdatePlaceBookmarkUseCase(private val updatePlaceBookmarkPort: UpdatePlaceBookmarkPort) {
    operator fun invoke(command: Command) {
        val updated = updatePlaceBookmarkPort.update(
            userId = command.userId,
            placeId = command.placeId,
            bookmarked = command.bookmarked,
        )
        if (!updated) {
            throw PlaceNotFoundException()
        }
    }

    data class Command(val userId: Long, val placeId: Long, val bookmarked: Boolean)
}
