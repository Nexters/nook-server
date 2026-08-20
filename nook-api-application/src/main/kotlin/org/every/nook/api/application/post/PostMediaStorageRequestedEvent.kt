package org.every.nook.api.application.post

import java.time.Instant

data class PostMediaStorageRequestedEvent(
    val postId: Long,
    val mediaType: String,
    val sourceUrl: String,
    val sequence: Int,
    val sourceThumbnailUrl: String? = null,
    val attempt: Int = 1,
    val availableAt: Instant? = null,
)
