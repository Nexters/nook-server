package org.every.nook.api.application.group

import java.math.BigDecimal

data class GroupPlacePage(
    val ownerNickname: String,
    val items: List<GroupPlaceSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

data class GroupPlaceSummary(
    val id: Long,
    val name: String,
    val city: String?,
    val address: String,
    val category: String?,
    val latitude: BigDecimal,
    val longitude: BigDecimal,
    val thumbnailUrl: String?,
    val tags: List<String> = emptyList(),
)
