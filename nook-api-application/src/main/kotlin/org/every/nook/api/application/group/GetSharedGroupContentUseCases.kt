package org.every.nook.api.application.group

import org.every.nook.api.application.group.error.SharedResourceNotFoundException
import org.every.nook.api.application.group.port.GroupPlaceQueryPort
import org.every.nook.api.application.group.port.GroupPostQueryPort
import org.every.nook.api.application.group.port.GroupSharePort
import org.every.nook.api.application.group.port.SharedPostViewerQueryPort
import org.every.nook.api.application.place.PlaceDetailView
import org.every.nook.api.application.place.port.SharedPlaceDetailQueryPort
import org.every.nook.api.application.post.model.SavedPostDetail
import org.every.nook.api.application.post.port.SavedPostQueryPort

class ListSharedGroupPostsUseCase(private val sharePort: GroupSharePort, private val queryPort: GroupPostQueryPort) {
    operator fun invoke(token: String, page: Int, size: Int): GroupPostPage {
        val access = sharePort.resolveActive(token)
        return queryPort.findAll(access.ownerId, access.groupId, page, size)
            ?: throw SharedResourceNotFoundException()
    }
}

class ListSharedGroupPlacesUseCase(private val sharePort: GroupSharePort, private val queryPort: GroupPlaceQueryPort) {
    operator fun invoke(token: String, page: Int, size: Int): GroupPlacePage {
        val access = sharePort.resolveActive(token)
        return queryPort.findPlaces(access.ownerId, access.groupId, page, size)
            ?: throw SharedResourceNotFoundException()
    }
}

class GetSharedPostDetailUseCase(
    private val sharePort: GroupSharePort,
    private val queryPort: SavedPostQueryPort,
    private val viewerQueryPort: SharedPostViewerQueryPort,
) {
    operator fun invoke(token: String, postId: Long, viewerId: Long?): SavedPostDetail {
        val access = sharePort.resolveActive(token)
        ensureSharedResource(sharePort.containsPost(access, postId))
        val detail = queryPort.findDetail(access.ownerId, postId) ?: throw SharedResourceNotFoundException()
        val viewerGroups = viewerId?.let { viewerQueryPort.findViewerGroups(it, postId) }.orEmpty()
        return detail.copy(groups = viewerGroups)
    }
}

class GetSharedPlaceDetailUseCase(
    private val sharePort: GroupSharePort,
    private val queryPort: SharedPlaceDetailQueryPort,
) {
    operator fun invoke(token: String, placeId: Long, page: Int, size: Int): PlaceDetailView {
        val access = sharePort.resolveActive(token)
        ensureSharedResource(sharePort.containsPlace(access, placeId))
        val detail = queryPort.findInGroup(access.ownerId, access.groupId, placeId, page, size)
            ?: throw SharedResourceNotFoundException()
        return detail
    }
}

private fun ensureSharedResource(found: Boolean) {
    if (!found) {
        throw SharedResourceNotFoundException()
    }
}
