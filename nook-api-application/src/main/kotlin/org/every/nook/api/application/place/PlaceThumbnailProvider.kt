package org.every.nook.api.application.place

fun interface PlaceThumbnailProvider {
    fun fetch(place: PlaceCandidate): PlaceSupplement?
}

object NoOpPlaceThumbnailProvider : PlaceThumbnailProvider {
    override fun fetch(place: PlaceCandidate): PlaceSupplement? = null
}
