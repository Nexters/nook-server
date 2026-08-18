package org.every.nook.api.application.place

data class SavedPlaceSearchItemView(val id: Long, val name: String, val address: String, val category: String?)

data class SavedPlaceSearchPageView(
    val items: List<SavedPlaceSearchItemView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)
