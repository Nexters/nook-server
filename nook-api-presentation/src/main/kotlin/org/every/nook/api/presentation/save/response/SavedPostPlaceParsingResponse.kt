package org.every.nook.api.presentation.save.response

import com.fasterxml.jackson.annotation.JsonInclude
import org.every.nook.api.application.save.FindSavedPostPlaceParsingUseCase
import org.every.nook.api.application.save.model.PlaceParsingStatusView
import org.every.nook.api.application.save.model.PlaceView
import java.math.BigDecimal

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SavedPostPlaceParsingResponse(
    val savedPostId: Long,
    val postId: Long,
    val placeParsingStatus: PlaceParsingStatusView,
    val failureReason: String? = null,
    val places: List<PlaceResponse>? = null,
) {
    companion object {
        fun from(result: FindSavedPostPlaceParsingUseCase.Result): SavedPostPlaceParsingResponse =
            SavedPostPlaceParsingResponse(
                savedPostId = result.savedPostId,
                postId = result.postId,
                placeParsingStatus = result.placeParsingStatus,
                failureReason = result.failureReason,
                places = result.places
                    .takeIf { result.placeParsingStatus == PlaceParsingStatusView.COMPLETED }
                    ?.map(PlaceResponse::from),
            )
    }
}

data class PlaceResponse(
    val id: Long,
    val provider: String,
    val externalPlaceId: String,
    val name: String,
    val address: String,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val category: String?,
    val phoneNumber: String?,
    val bookmarked: Boolean,
) {
    companion object {
        fun from(place: PlaceView): PlaceResponse = PlaceResponse(
            id = place.id,
            provider = place.provider,
            externalPlaceId = place.externalPlaceId,
            name = place.name,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            category = place.category,
            phoneNumber = place.phoneNumber,
            bookmarked = place.bookmarked,
        )
    }
}
