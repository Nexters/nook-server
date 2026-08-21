package org.every.nook.api.application.place

fun interface PlaceThumbnailProvider {
    fun fetch(request: Request): PlaceSupplement?

    fun fetchAll(requests: List<Request>): List<PlaceSupplement?> = requests.map(::fetch)

    fun fetchAll(
        requests: List<Request>,
        onPhotosResolved: (Request, PlaceSupplement) -> Unit,
    ): List<PlaceSupplement?> = fetchAll(requests).also { supplements ->
        requests.zip(supplements).forEach { (request, supplement) ->
            supplement?.takeIf { it.photoUrls.isNotEmpty() }?.let { onPhotosResolved(request, it) }
        }
    }

    data class Request(
        val place: PlaceCandidate,
        val sourcePostId: Long? = null,
        val sourceMediaSequence: Int? = null,
        val postMediaFallbackAllowed: Boolean = false,
    )
}

object NoOpPlaceThumbnailProvider : PlaceThumbnailProvider {
    override fun fetch(request: PlaceThumbnailProvider.Request): PlaceSupplement? = null
}
