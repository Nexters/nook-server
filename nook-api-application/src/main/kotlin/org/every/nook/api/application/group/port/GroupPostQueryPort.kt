package org.every.nook.api.application.group.port

import org.every.nook.api.application.group.GroupPostPage

fun interface GroupPostQueryPort {
    fun findAll(userId: Long, groupId: Long, page: Int, size: Int): GroupPostPage?
}
