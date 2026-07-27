package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.port.UpdatePostMemoPort

class UpdatePostMemoUseCase(private val updatePostMemoPort: UpdatePostMemoPort) {
    operator fun invoke(command: Command) {
        if (!updatePostMemoPort.update(command.userId, command.postId, command.memo)) {
            throw PostNotFoundException()
        }
    }

    data class Command(val userId: Long, val postId: Long, val memo: String?)
}
