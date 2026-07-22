package org.every.nook.api.domain.group

data class GroupPost(val groupId: Long, val userSavedPostId: Long, val id: Long? = null) {
    init {
        require(id == null || id > 0) { "Group post id must be positive" }
        require(groupId > 0) { "Group id must be positive" }
        require(userSavedPostId > 0) { "User saved post id must be positive" }
    }
}
