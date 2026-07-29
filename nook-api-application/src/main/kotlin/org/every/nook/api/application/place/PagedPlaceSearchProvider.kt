package org.every.nook.api.application.place

fun interface PagedPlaceSearchProvider {
    fun searchPage(request: PlaceSearchProvider.Request): PlaceCandidatePage
}

data class PlaceCandidatePage(val items: List<PlaceCandidate>, val page: Int, val size: Int, val hasNext: Boolean)
