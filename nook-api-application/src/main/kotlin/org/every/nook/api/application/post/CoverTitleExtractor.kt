package org.every.nook.api.application.post

fun interface CoverTitleExtractor {
    fun extract(request: Request): String?

    data class Request(val imageUrl: String)
}
