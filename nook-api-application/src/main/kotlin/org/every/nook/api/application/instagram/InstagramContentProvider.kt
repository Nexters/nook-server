package org.every.nook.api.application.instagram

fun interface InstagramContentProvider {
    fun extract(url: InstagramContentUrl): ExtractedInstagramContent
}
