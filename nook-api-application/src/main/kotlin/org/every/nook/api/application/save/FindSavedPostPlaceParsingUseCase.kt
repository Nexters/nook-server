package org.every.nook.api.application.save

import org.every.nook.api.application.save.error.SavedPostNotFoundException
import org.every.nook.api.application.save.model.PlaceParsingStatusView
import org.every.nook.api.application.save.model.PlaceView
import org.every.nook.api.application.save.port.FindSavedPostPlaceParsingPort

class FindSavedPostPlaceParsingUseCase(private val findSavedPostPlaceParsingPort: FindSavedPostPlaceParsingPort) {
    operator fun invoke(query: Query): Result {
        val snapshot = findSavedPostPlaceParsingPort.find(
            userId = query.userId,
            savedPostId = query.savedPostId,
        ) ?: throw SavedPostNotFoundException()

        return Result(
            savedPostId = snapshot.savedPostId,
            postId = snapshot.postId,
            placeParsingStatus = PlaceParsingStatusView.from(snapshot.placeParsingStatus),
            failureReason = snapshot.failureReason,
            places = snapshot.places.map(PlaceView::from),
        )
    }

    data class Query(val userId: Long, val savedPostId: Long)

    data class Result(
        val savedPostId: Long,
        val postId: Long,
        val placeParsingStatus: PlaceParsingStatusView,
        val failureReason: String?,
        val places: List<PlaceView>,
    )
}
