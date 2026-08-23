package org.every.nook.api.application.post

import org.every.nook.api.application.post.error.PostNotFoundException
import org.every.nook.api.application.post.model.PlaceParsingFailureReasonView
import org.every.nook.api.application.post.model.PlaceParsingStatusView
import org.every.nook.api.application.post.model.PlaceView
import org.every.nook.api.application.post.port.FindPostPlaceParsingPort

class FindPostPlaceParsingUseCase(private val findPostPlaceParsingPort: FindPostPlaceParsingPort) {
    operator fun invoke(query: Query): Result {
        val snapshot = findPostPlaceParsingPort.find(
            userId = query.userId,
            postId = query.postId,
        ) ?: throw PostNotFoundException()

        return Result(
            postId = snapshot.postId,
            placeParsingStatus = PlaceParsingStatusView.from(snapshot.placeParsingStatus),
            failureReason = PlaceParsingFailureReasonView.from(snapshot.placeParsingStatus),
            places = snapshot.places.map { relatedPlace ->
                PlaceView.from(
                    relatedPlace.place,
                    relatedPlace.bookmarked,
                    relatedPlace.thumbnailUrl,
                    relatedPlace.thumbnailParsingStatus,
                    relatedPlace.tags,
                    relatedPlace.memo,
                )
            },
        )
    }

    data class Query(val userId: Long, val postId: Long)

    data class Result(
        val postId: Long,
        val placeParsingStatus: PlaceParsingStatusView,
        val failureReason: String?,
        val places: List<PlaceView>,
    )
}
