package org.every.nook.api.application.place

import java.time.Instant

data class PlaceThumbnailRequestedEvent(val postId: Long, val place: PlaceCandidate, val availableAt: Instant? = null)
