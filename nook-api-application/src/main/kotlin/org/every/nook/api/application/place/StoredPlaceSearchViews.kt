package org.every.nook.api.application.place

import java.math.BigDecimal

data class StoredPlaceSearchView(
    val id: Long,
    val name: String,
    val address: String,
    val category: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val thumbnailUrl: String?,
    val tags: List<String>,
    val bookmarked: Boolean,
)

data class StoredPlaceSearchSliceView(
    val items: List<StoredPlaceSearchView>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
)
