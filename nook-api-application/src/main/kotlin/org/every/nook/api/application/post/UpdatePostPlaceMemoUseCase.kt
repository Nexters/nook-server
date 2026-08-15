package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.port.UpdatePostPlaceMemoPort

class UpdatePostPlaceMemoUseCase(private val updatePostPlaceMemoPort: UpdatePostPlaceMemoPort) {
    operator fun invoke(command: Command) {
        if (!updatePostPlaceMemoPort.update(command.userId, command.postId, command.placeId, command.memo)) {
            throw PostNotFoundException()
        }
    }

    data class Command(val userId: Long, val postId: Long, val placeId: Long, val memo: String?)
}
