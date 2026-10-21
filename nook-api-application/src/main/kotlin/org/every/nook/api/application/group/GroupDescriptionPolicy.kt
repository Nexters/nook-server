package org.every.nook.api.application.group

import org.every.nook.api.domain.group.Group

object GroupDescriptionPolicy {
    const val MAX_LENGTH = Group.MAX_DESCRIPTION_LENGTH

    fun isValid(description: String?): Boolean = Group.isValidDescription(description)
}
