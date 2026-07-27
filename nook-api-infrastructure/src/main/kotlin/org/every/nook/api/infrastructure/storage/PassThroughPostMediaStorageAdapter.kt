package org.every.nook.api.infrastructure.storage

import org.every.nook.api.application.post.port.PostMediaStoragePort
import org.every.nook.api.domain.post.PostMedia
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "external.media-storage",
    name = ["enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
class PassThroughPostMediaStorageAdapter : PostMediaStoragePort {
    override fun store(media: PostMedia): PostMedia = media
}
