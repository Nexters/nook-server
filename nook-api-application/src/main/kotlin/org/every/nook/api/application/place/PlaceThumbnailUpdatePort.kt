package org.every.nook.api.application.place

import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus

interface PlaceThumbnailUpdatePort {
    fun update(
        provider: String,
        externalPlaceId: String,
        status: PlaceThumbnailParsingStatus,
        supplement: PlaceSupplement? = null,
    )
}
