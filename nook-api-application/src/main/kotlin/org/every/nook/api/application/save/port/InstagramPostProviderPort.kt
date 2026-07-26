package org.every.nook.api.application.save.port

import org.every.nook.api.domain.post.Post

fun interface InstagramPostProviderPort {
    fun fetch(instagramUrl: String): Post
}
