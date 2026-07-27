package org.every.nook.api.domain.post

import java.time.Instant

data class Post(
    val source: PostSource,
    val canonicalUrl: String,
    val authorIdentifier: String? = null,
    val title: String? = null,
    val body: String? = null,
    val publishedAt: Instant? = null,
    val sourceLocationTag: String? = null,
    val hashtags: List<String> = emptyList(),
    val media: List<PostMedia> = emptyList(),
    val id: Long? = null,
) {
    init {
        require(id == null || id > 0) { "Post id must be positive" }
        require(canonicalUrl.isNotBlank()) { "Post canonical url must not be blank" }
        require(canonicalUrl.length <= MAX_CANONICAL_URL_LENGTH) {
            "Post canonical url must not exceed $MAX_CANONICAL_URL_LENGTH characters"
        }
        require(authorIdentifier == null || authorIdentifier.length <= MAX_AUTHOR_IDENTIFIER_LENGTH) {
            "Post author identifier must not exceed $MAX_AUTHOR_IDENTIFIER_LENGTH characters"
        }
        require(title == null || title.length <= MAX_TITLE_LENGTH) {
            "Post title must not exceed $MAX_TITLE_LENGTH characters"
        }
        require(sourceLocationTag == null || sourceLocationTag.length <= MAX_SOURCE_LOCATION_TAG_LENGTH) {
            "Post source location tag must not exceed $MAX_SOURCE_LOCATION_TAG_LENGTH characters"
        }
        require(hashtags.all { it.isNotBlank() && it.length <= MAX_HASHTAG_LENGTH }) {
            "Post hashtags must not be blank or exceed $MAX_HASHTAG_LENGTH characters"
        }
        require(hashtags.distinct().size == hashtags.size) {
            "Post hashtags must be unique"
        }
        require(media.map(PostMedia::sequence).distinct().size == media.size) {
            "Post media sequence must be unique"
        }
    }

    companion object {
        const val MAX_CANONICAL_URL_LENGTH = 2048
        const val MAX_AUTHOR_IDENTIFIER_LENGTH = 255
        const val MAX_TITLE_LENGTH = 500
        const val MAX_SOURCE_LOCATION_TAG_LENGTH = 500
        const val MAX_HASHTAG_LENGTH = 100
    }
}
