package org.every.nook.api.application.place

import org.every.nook.api.application.place.error.PlaceNotFoundException
import org.every.nook.api.application.place.port.DisconnectPostPlacePort
import org.every.nook.api.application.post.error.PostNotFoundException

class DisconnectPostPlaceUseCase(private val disconnectPostPlacePort: DisconnectPostPlacePort) {
    operator fun invoke(command: Command) {
        when (disconnectPostPlacePort.disconnect(command.userId, command.postId, command.placeId)) {
            DisconnectPostPlacePort.Result.DISCONNECTED -> Unit
            DisconnectPostPlacePort.Result.POST_NOT_FOUND -> throw PostNotFoundException()
            DisconnectPostPlacePort.Result.PLACE_NOT_CONNECTED -> throw PlaceNotFoundException()
        }
    }

    data class Command(val userId: Long, val postId: Long, val placeId: Long)
}
