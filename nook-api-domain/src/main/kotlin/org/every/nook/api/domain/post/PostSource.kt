package org.every.nook.api.domain.post

data class PostSource(val type: String, val externalPostId: String) {
    init {
        require(SOURCE_TYPE_PATTERN.matches(type)) {
            "Post source type must contain only uppercase letters, numbers, and underscores"
        }
        require(type.length <= MAX_SOURCE_TYPE_LENGTH) {
            "Post source type must not exceed $MAX_SOURCE_TYPE_LENGTH characters"
        }
        require(externalPostId.isNotBlank()) { "External post id must not be blank" }
        require(externalPostId.length <= MAX_EXTERNAL_POST_ID_LENGTH) {
            "External post id must not exceed $MAX_EXTERNAL_POST_ID_LENGTH characters"
        }
    }

    companion object {
        const val MAX_SOURCE_TYPE_LENGTH = 50
        const val MAX_EXTERNAL_POST_ID_LENGTH = 255
        private val SOURCE_TYPE_PATTERN = Regex("[A-Z][A-Z0-9_]*")
    }
}
