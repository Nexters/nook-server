package org.every.nook.api.domain.post

data class PostPlace(val postId: Long, val placeId: Long, val sequence: Int, val id: Long? = null) {
    init {
        require(id == null || id > 0) { "Post place id must be positive" }
        require(postId > 0) { "Post id must be positive" }
        require(placeId > 0) { "Place id must be positive" }
        require(sequence >= 0) { "Post place sequence must not be negative" }
    }
}
