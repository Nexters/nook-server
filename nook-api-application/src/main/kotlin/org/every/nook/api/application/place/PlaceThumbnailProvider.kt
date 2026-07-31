package org.every.nook.api.application.place

fun interface PlaceThumbnailProvider {
    fun fetchThumbnailUrl(place: PlaceCandidate): String?
}

object NoOpPlaceThumbnailProvider : PlaceThumbnailProvider {
    override fun fetchThumbnailUrl(place: PlaceCandidate): String? = null
}
