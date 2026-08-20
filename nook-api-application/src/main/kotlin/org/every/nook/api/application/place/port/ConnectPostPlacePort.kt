package org.every.nook.api.application.place.port

import org.every.nook.api.application.place.PlaceCandidate

fun interface ConnectPostPlacePort {
    fun connect(userId: Long, savedPostId: Long, candidate: PlaceCandidate): Result

    sealed interface Result {
        data class Connected(val placeId: Long) : Result

        data object PostNotFound : Result

        data object ParsingInProgress : Result
    }
}
