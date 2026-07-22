package org.every.nook.api.domain.post

data class PostMedia(val type: MediaType, val url: String, val sequence: Int) {
    init {
        require(url.isNotBlank()) { "Post media url must not be blank" }
        require(url.length <= MAX_MEDIA_URL_LENGTH) {
            "Post media url must not exceed $MAX_MEDIA_URL_LENGTH characters"
        }
        require(sequence >= 0) { "Post media sequence must not be negative" }
    }

    enum class MediaType {
        IMAGE,
        VIDEO,
    }

    companion object {
        const val MAX_MEDIA_URL_LENGTH = 2048
    }
}
