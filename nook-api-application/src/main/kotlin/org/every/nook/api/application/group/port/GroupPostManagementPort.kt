package org.every.nook.api.application.group.port

interface GroupPostManagementPort {
    fun replace(userId: Long, savedPostId: Long, groupIds: Set<Long>): ReplaceResult

    enum class ReplaceResult {
        Updated,
        PostNotFound,
        GroupNotFound,
    }
}
