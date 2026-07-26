package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.port.UpdatePostPlaceBookmarkPort

class UpdatePostPlaceBookmarkUseCase(private val updatePostPlaceBookmarkPort: UpdatePostPlaceBookmarkPort) {
    operator fun invoke(command: Command) {
        val updated = updatePostPlaceBookmarkPort.update(
            userId = command.userId,
            postId = command.postId,
            placeId = command.placeId,
            bookmarked = command.bookmarked,
        )
        if (!updated) {
            throw PostNotFoundException()
        }
    }

    data class Command(val userId: Long, val postId: Long, val placeId: Long, val bookmarked: Boolean)
}
