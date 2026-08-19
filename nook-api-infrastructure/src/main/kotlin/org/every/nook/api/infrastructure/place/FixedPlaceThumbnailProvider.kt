package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider

class FixedPlaceThumbnailProvider(private val thumbnailUrl: String) : PlaceThumbnailProvider {
    fun fetch(place: PlaceCandidate): PlaceSupplement = fetch(PlaceThumbnailProvider.Request(place))

    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement = PlaceSupplement(
        openingHours = null,
        photoUrls = listOf(thumbnailUrl),
    )
}
