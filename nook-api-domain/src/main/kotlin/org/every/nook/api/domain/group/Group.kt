package org.every.nook.api.domain.group

data class Group(val userId: Long, val name: String, val id: Long? = null) {
    init {
        require(id == null || id > 0) { "Group id must be positive" }
        require(userId > 0) { "User id must be positive" }
        require(name.isNotBlank()) { "Group name must not be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "Group name must not exceed $MAX_NAME_LENGTH characters" }
    }

    companion object {
        const val MAX_NAME_LENGTH = 100
    }
}
