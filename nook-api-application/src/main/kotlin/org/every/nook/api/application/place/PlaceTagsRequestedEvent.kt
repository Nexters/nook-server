package org.every.nook.api.application.place

data class PlaceTagsRequestedEvent(val postId: Long, val placeId: Long, val place: PlaceCandidate)
