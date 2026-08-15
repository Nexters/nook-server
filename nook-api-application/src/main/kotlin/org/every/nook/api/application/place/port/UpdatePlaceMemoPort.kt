package org.every.nook.api.application.place.port

fun interface UpdatePlaceMemoPort {
    fun update(userId: Long, placeId: Long, memo: String?): Boolean
}
