package org.every.nook.api.application.place

fun interface PlaceTagSourcePort {
    fun find(postId: Long): PlaceTagSource?
}

data class PlaceTagSource(val body: String?, val hashtags: List<String>)

fun interface PlaceTagUpdatePort {
    fun replace(postId: Long, placeId: Long, tags: List<InferredPlaceTag>)
}

fun interface PlaceTagBackfillPort {
    fun findAll(): List<PlaceTagsRequestedEvent>
}
