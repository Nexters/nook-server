package org.every.nook.api.application.place

import java.time.Instant

data class PlaceThumbnailsRequestedEvent(
    val postId: Long,
    val requests: List<PlaceThumbnailProvider.Request>,
    val availableAt: Instant? = null,
) {
    init {
        require(requests.isNotEmpty()) { "Place thumbnail requests must not be empty" }
    }
}
