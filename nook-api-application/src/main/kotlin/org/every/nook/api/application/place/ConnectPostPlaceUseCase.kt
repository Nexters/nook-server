package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.application.post.error.PostNotFoundException

class ConnectPostPlaceUseCase(
    private val selectionTokenPort: PlaceSelectionTokenPort,
    private val connectPostPlacePort: ConnectPostPlacePort,
) {
    operator fun invoke(command: Command): Result {
        val candidate = selectionTokenPort.verify(command.userId, command.selectionToken)
            ?: throw InvalidPlaceSelectionException()
        val result = connectPostPlacePort.connect(
            userId = command.userId,
            savedPostId = command.postId,
            candidate = candidate,
        )
        return mapResult(result)
    }

    private fun mapResult(result: ConnectPostPlacePort.Result): Result = when (result) {
        is ConnectPostPlacePort.Result.Connected -> Result(result.placeId)
        ConnectPostPlacePort.Result.PostNotFound -> postNotFound()
        ConnectPostPlacePort.Result.ParsingInProgress -> parsingInProgress()
    }

    private fun postNotFound(): Nothing = throw PostNotFoundException()

    private fun parsingInProgress(): Nothing = throw PlaceParsingInProgressException()

    data class Command(val userId: Long, val postId: Long, val selectionToken: String)

    data class Result(val placeId: Long)
}
