package org.every.nook.api.application.save

import org.every.nook.api.application.save.error.SavedPostNotFoundException
import org.every.nook.api.application.save.port.UpdateSavedPostPlaceBookmarkPort

class UpdateSavedPostPlaceBookmarkUseCase(
    private val updateSavedPostPlaceBookmarkPort: UpdateSavedPostPlaceBookmarkPort,
) {
    operator fun invoke(command: Command) {
        val updated = updateSavedPostPlaceBookmarkPort.update(
            userId = command.userId,
            savedPostId = command.savedPostId,
            placeId = command.placeId,
            bookmarked = command.bookmarked,
        )
        if (!updated) {
            throw SavedPostNotFoundException()
        }
    }

    data class Command(val userId: Long, val savedPostId: Long, val placeId: Long, val bookmarked: Boolean)
}
