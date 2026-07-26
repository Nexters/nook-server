package org.every.nook.api.presentation.save.response

import org.every.nook.api.application.save.SaveInstagramPostUseCase
import org.every.nook.api.application.save.model.PlaceParsingStatusView

data class SavedPostResponse(val savedPostId: Long, val postId: Long, val placeParsingStatus: PlaceParsingStatusView) {
    companion object {
        fun from(result: SaveInstagramPostUseCase.Result): SavedPostResponse = SavedPostResponse(
            savedPostId = result.savedPostId,
            postId = result.postId,
            placeParsingStatus = result.placeParsingStatus,
        )
    }
}
