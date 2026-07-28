package org.every.nook.api.application.post.port

import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.model.SavedPostPage

interface SavedPostQueryPort {
    fun findAll(userId: Long, page: Int, size: Int): SavedPostPage

    fun findDetail(userId: Long, postId: Long): SavedPostDetail?
}
