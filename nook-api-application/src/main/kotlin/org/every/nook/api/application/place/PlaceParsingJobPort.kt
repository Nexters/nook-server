package org.every.nook.api.application.place

interface PlaceParsingJobPort {
    fun claimNext(): ClaimedPlaceParsingJob?

    fun complete(postId: Long, places: List<PlaceCandidate>)

    fun fail(postId: Long, reason: String)
}
