package org.every.nook.api.application.place

data class SavedPlaceSearchItemView(
    val id: Long,
    val name: String,
    val address: String,
    val category: String?,
    val thumbnailUrl: String?,
)

data class SavedPlaceSearchGroupView(val id: Long, val name: String, val color: String, val matchedPlaceCount: Long)

data class SavedPlaceSearchPageView(
    val items: List<SavedPlaceSearchItemView>,
    val groups: List<SavedPlaceSearchGroupView>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)
