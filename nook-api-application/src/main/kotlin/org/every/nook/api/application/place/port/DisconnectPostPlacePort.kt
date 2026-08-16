package org.every.nook.api.application.place.port

fun interface DisconnectPostPlacePort {
    fun disconnect(userId: Long, savedPostId: Long, placeId: Long): Result

    enum class Result {
        DISCONNECTED,
        POST_NOT_FOUND,
        PLACE_NOT_CONNECTED,
    }
}
