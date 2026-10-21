package org.every.nook.api.application.group.port

import org.every.nook.api.application.group.GroupView
import org.every.nook.api.domain.group.GroupColor

interface GroupPort {
    fun findAll(userId: Long): List<GroupView>

    fun findOwned(userId: Long): List<GroupView>

    fun create(userId: Long, name: String, color: GroupColor, description: String? = null): GroupView

    /** A null description preserves the existing value; an empty string clears it. */
    fun update(userId: Long, groupId: Long, name: String, color: GroupColor, description: String? = null): UpdateResult

    fun delete(userId: Long, groupId: Long): Boolean

    sealed interface UpdateResult {
        data class Updated(val group: GroupView) : UpdateResult

        data object NotFound : UpdateResult
    }
}
