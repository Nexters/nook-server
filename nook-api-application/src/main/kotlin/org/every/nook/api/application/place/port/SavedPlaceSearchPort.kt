package org.every.nook.api.application.place.port

import org.every.nook.api.application.place.SavedPlaceSearchPageView

fun interface SavedPlaceSearchPort {
    fun search(userId: Long, keyword: String, groupId: Long?, page: Int, size: Int): SavedPlaceSearchPageView
}
