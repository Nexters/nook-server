package org.every.nook.api.application.group

import org.every.nook.api.application.post.model.SavedPostSummary

data class GroupPostPage(
    val ownerNickname: String,
    val items: List<GroupPostSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
)

data class GroupPostSummary(val post: SavedPostSummary, val placeCount: Long)
