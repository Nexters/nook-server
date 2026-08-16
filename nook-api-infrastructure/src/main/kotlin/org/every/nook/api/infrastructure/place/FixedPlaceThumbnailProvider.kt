package org.every.nook.api.infrastructure.place

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceThumbnailProvider

class FixedPlaceThumbnailProvider(private val thumbnailUrl: String) : PlaceThumbnailProvider {
    override fun fetch(place: PlaceCandidate): PlaceSupplement = PlaceSupplement(
        openingHours = null,
        photoUrls = listOf(thumbnailUrl),
    )
}
