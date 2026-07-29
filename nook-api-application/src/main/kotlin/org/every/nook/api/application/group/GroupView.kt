package org.every.nook.api.application.group

data class GroupView(
    val id: Long,
    val name: String,
    val color: String,
    val postCount: Long,
    val thumbnailUrls: List<String> = emptyList(),
)
