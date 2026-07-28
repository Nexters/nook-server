package org.every.nook.api.application.content

import org.every.nook.api.domain.post.PostSource

fun interface PostSourceResolver {
    fun resolve(url: String): PostSource?
}
