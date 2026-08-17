package org.every.nook.api.application.post.port

fun interface SaveSharedPostPort {
    fun save(userId: Long, sharedPostId: Long, groupIds: Set<Long>): Long
}
