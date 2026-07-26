package org.every.nook.api.application.post

fun interface PostTitleGenerator {
    fun generate(request: Request): String

    data class Request(val body: String?, val hashtags: List<String>, val sourceLocationTag: String?)
}
