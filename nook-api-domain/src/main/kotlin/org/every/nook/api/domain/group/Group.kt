package org.every.nook.api.domain.group

import java.util.regex.Pattern

data class Group(
    val userId: Long,
    val name: String,
    val color: GroupColor,
    val id: Long? = null,
    val description: String? = null,
) {
    init {
        require(id == null || id > 0) { "Group id must be positive" }
        require(userId > 0) { "User id must be positive" }
        require(isValidDescription(description)) {
            "Group description must not exceed $MAX_DESCRIPTION_LENGTH characters"
        }
        require(name.isNotBlank()) { "Group name must not be blank" }
        require(name.length <= MAX_NAME_LENGTH) { "Group name must not exceed $MAX_NAME_LENGTH characters" }
    }

    companion object {
        const val MAX_NAME_LENGTH = 20
        const val MAX_DESCRIPTION_LENGTH = 150
        private val GRAPHEME = Pattern.compile("\\X")

        fun isValidDescription(description: String?): Boolean = description == null ||
            GRAPHEME.matcher(description.trim()).results().limit(MAX_DESCRIPTION_LENGTH + 1L).count() <=
            MAX_DESCRIPTION_LENGTH
    }
}
