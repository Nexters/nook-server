package org.every.nook.api.domain.save

data class UserSavedPost(val userId: Long, val postId: Long, val memo: String? = null, val id: Long? = null) {
    init {
        require(id == null || id > 0) { "User saved post id must be positive" }
        require(userId > 0) { "User id must be positive" }
        require(postId > 0) { "Post id must be positive" }
    }
}
