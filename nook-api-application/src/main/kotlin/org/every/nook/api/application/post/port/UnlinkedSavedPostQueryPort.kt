package org.every.nook.api.application.post.port

import org.every.nook.api.application.post.model.SavedPostPage

interface UnlinkedSavedPostQueryPort {
    fun countUnlinked(userId: Long): Long
    fun findUnlinked(userId: Long, page: Int, size: Int): SavedPostPage
}
