package org.every.nook.api.application.group.port

import org.every.nook.api.application.post.model.SavedPostGroup

fun interface SharedPostViewerQueryPort {
    fun findViewerGroups(viewerId: Long, sharedSavedPostId: Long): List<SavedPostGroup>
}
