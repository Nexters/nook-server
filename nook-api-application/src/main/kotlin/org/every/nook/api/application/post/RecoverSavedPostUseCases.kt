package org.every.nook.api.application.post

import org.every.nook.api.application.post.model.PostProcessingView

data class SavedPostRecovery(val postId: Long, val processing: PostProcessingView, val requested: Boolean = false)

interface SavedPostRecoveryPort {
    fun find(userId: Long, savedPostId: Long): SavedPostRecovery
    fun request(userId: Long, savedPostId: Long): SavedPostRecovery
}

class GetSavedPostRecoveryUseCase(private val port: SavedPostRecoveryPort) {
    operator fun invoke(userId: Long, savedPostId: Long): SavedPostRecovery {
        require(userId > 0 && savedPostId > 0)
        return port.find(userId, savedPostId)
    }
}

class RequestSavedPostRecoveryUseCase(private val port: SavedPostRecoveryPort) {
    operator fun invoke(userId: Long, savedPostId: Long): SavedPostRecovery {
        require(userId > 0 && savedPostId > 0)
        return port.request(userId, savedPostId)
    }
}
