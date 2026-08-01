package org.every.nook.api.application.place

fun interface PlaceThumbnailUpdatePort {
    fun update(provider: String, externalPlaceId: String, thumbnailUrl: String)
}
