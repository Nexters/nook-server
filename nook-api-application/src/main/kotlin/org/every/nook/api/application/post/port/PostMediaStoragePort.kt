package org.every.nook.api.application.post.port

import org.every.nook.api.domain.post.PostMedia

fun interface PostMediaStoragePort {
    fun store(media: PostMedia): PostMedia
}
