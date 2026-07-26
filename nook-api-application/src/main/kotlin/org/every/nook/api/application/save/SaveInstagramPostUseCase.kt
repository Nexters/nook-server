package org.every.nook.api.application.save

import org.every.nook.api.application.save.error.InvalidInstagramPostUrlException
import org.every.nook.api.application.save.model.PlaceParsingStatusView
import org.every.nook.api.application.save.port.InstagramPostProviderPort
import org.every.nook.api.application.save.port.PostMediaStoragePort
import org.every.nook.api.application.save.port.SaveInstagramPostPort

class SaveInstagramPostUseCase(
    private val instagramPostProviderPort: InstagramPostProviderPort,
    private val postMediaStoragePort: PostMediaStoragePort,
    private val saveInstagramPostPort: SaveInstagramPostPort,
) {
    operator fun invoke(command: Command): Result {
        if (command.userId <= 0 || command.instagramUrl.isBlank()) {
            throw InvalidInstagramPostUrlException()
        }

        val providedPost = instagramPostProviderPort.fetch(command.instagramUrl)
        val storedPost = providedPost.copy(
            media = providedPost.media.map(postMediaStoragePort::store),
        )
        val saved = saveInstagramPostPort.save(command.userId, storedPost)

        return Result(
            savedPostId = saved.savedPostId,
            postId = saved.postId,
            placeParsingStatus = PlaceParsingStatusView.from(saved.placeParsingStatus),
        )
    }

    data class Command(val userId: Long, val instagramUrl: String)

    data class Result(val savedPostId: Long, val postId: Long, val placeParsingStatus: PlaceParsingStatusView)
}
