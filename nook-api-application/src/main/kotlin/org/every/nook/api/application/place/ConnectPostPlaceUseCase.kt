package org.every.nook.api.application.place

import org.every.nook.api.application.place.port.ConnectPostPlacePort
import org.every.nook.api.application.post.error.PostNotFoundException

class ConnectPostPlaceUseCase(
    private val selectionTokenPort: PlaceSelectionTokenPort,
    private val connectPostPlacePort: ConnectPostPlacePort,
    private val thumbnailProvider: PlaceThumbnailProvider = NoOpPlaceThumbnailProvider,
) {
    operator fun invoke(command: Command): Result {
        val candidate = selectionTokenPort.verify(command.userId, command.selectionToken)
            ?: throw InvalidPlaceSelectionException()
        val supplement = fetchSupplement(command, candidate)
        val result = connectPostPlacePort.connect(
            userId = command.userId,
            savedPostId = command.postId,
            candidate = candidate,
            supplement = supplement,
        )
        return mapResult(result)
    }

    private fun fetchSupplement(command: Command, candidate: PlaceCandidate): PlaceSupplement? = runCatching {
        thumbnailProvider.fetch(PlaceThumbnailProvider.Request(candidate))
    }.onFailure { exception ->
        logger.warn(exception) {
            "Manual place thumbnail skipped: userId=${command.userId}, postId=${command.postId}, " +
                "provider=${candidate.provider}, externalPlaceId=${candidate.externalPlaceId}"
        }
    }.getOrNull().also { supplement ->
        logger.info {
            "Manual place thumbnail resolved: userId=${command.userId}, postId=${command.postId}, " +
                "provider=${candidate.provider}, externalPlaceId=${candidate.externalPlaceId}, " +
                "supplementFound=${supplement != null}"
        }
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

    private companion object {
        val logger = mu.KotlinLogging.logger {}
    }
}
