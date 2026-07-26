package org.every.nook.api.application.content

interface PostContentExtractor {
    fun supports(url: String): Boolean

    fun extract(url: String): ExtractedPostContent
}
