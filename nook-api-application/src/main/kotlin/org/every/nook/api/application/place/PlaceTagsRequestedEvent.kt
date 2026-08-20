package org.every.nook.api.application.place

data class PlaceTagsRequestedEvent(val postId: Long, val places: List<Place>) {
    init {
        require(places.isNotEmpty()) { "Place tag targets must not be empty" }
        require(places.map(Place::placeId).distinct().size == places.size) {
            "Place tag target IDs must be unique"
        }
    }

    data class Place(val placeId: Long, val candidate: PlaceCandidate)
}
