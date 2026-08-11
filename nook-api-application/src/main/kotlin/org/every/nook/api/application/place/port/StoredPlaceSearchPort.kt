package org.every.nook.api.application.place.port

import org.every.nook.api.application.place.StoredPlaceSearchView

fun interface SearchAllStoredPlacesPort {
    fun searchAll(userId: Long, keyword: String, offset: Int, limit: Int): List<StoredPlaceSearchView>
}

fun interface SearchMyStoredPlacesPort {
    fun searchMine(userId: Long, keyword: String, offset: Int, limit: Int): List<StoredPlaceSearchView>
}
