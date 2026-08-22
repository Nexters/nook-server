package org.every.nook.api.application.content

import org.every.nook.api.domain.post.Post

data class ExtractedPostContent(
    val post: Post,
    val hashtags: List<String>,
    val sourceLocationNames: List<String>,
    val sourceProfileHints: List<SourceProfileHint> = emptyList(),
)

data class SourceProfileHint(val displayName: String, val username: String)
