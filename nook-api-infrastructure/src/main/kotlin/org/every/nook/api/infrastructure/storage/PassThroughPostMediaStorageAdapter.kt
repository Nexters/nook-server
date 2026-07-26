package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.springframework.stereotype.Component

@Component
class PassThroughPostMediaStorageAdapter : PostMediaStoragePort {
    override fun store(media: PostMedia): PostMedia = media
}
