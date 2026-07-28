package org.every.nook.api.application.post.port

import org.every.nook.api.domain.post.PostSource

fun interface ReusePostPort {
    fun reuse(userId: Long, source: PostSource, memo: String?, groupIds: Set<Long>): CreatedPost
}
