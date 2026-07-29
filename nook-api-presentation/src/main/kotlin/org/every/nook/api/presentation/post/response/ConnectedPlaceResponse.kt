package org.every.nook.api.presentation.post.response

import io.swagger.v3.oas.annotations.media.Schema
import org.every.nook.api.application.place.ConnectPostPlaceUseCase

data class ConnectedPlaceResponse(
    @field:Schema(description = "연결된 장소 식별자")
    val placeId: Long,
) {
    companion object {
        fun from(result: ConnectPostPlaceUseCase.Result): ConnectedPlaceResponse =
            ConnectedPlaceResponse(result.placeId)
    }
}
