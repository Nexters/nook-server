package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.port.DeleteSavedPostPort

class DeleteSavedPostUseCase(private val deleteSavedPostPort: DeleteSavedPostPort) {
    operator fun invoke(command: Command) {
        if (!deleteSavedPostPort.delete(command.userId, command.postId)) {
            throw PostNotFoundException()
        }
    }

    data class Command(val userId: Long, val postId: Long)
}
