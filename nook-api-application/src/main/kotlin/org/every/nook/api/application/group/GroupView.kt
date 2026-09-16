package org.every.nook.api.application.group

import java.time.Instant

data class GroupView(
    val id: Long,
    val name: String,
    val color: String,
    val postCount: Long,
    val lastSavedAt: Instant? = null,
    val thumbnailUrls: List<String> = emptyList(),
    val accessType: GroupAccessType = GroupAccessType.OWNED,
    val owner: GroupOwnerView? = null,
    val shareToken: String? = null,
)

enum class GroupAccessType { OWNED, SHARED }

data class GroupOwnerView(val nickname: String, val profileImageUrl: String?)
