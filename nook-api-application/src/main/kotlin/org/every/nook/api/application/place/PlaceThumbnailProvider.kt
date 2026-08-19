package org.every.nook.api.application.place

fun interface PlaceThumbnailProvider {
    fun fetch(request: Request): PlaceSupplement?

    fun fetchAll(requests: List<Request>): List<PlaceSupplement?> = requests.map(::fetch)

    data class Request(val place: PlaceCandidate, val sourcePostId: Long? = null, val sourceMediaSequence: Int? = null)
}

object NoOpPlaceThumbnailProvider : PlaceThumbnailProvider {
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? = null
}
