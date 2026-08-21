package org.every.nook.api.application.post

fun interface PostTitleInference {
    fun infer(request: Request): String?

    data class Request(val body: String?, val hashtags: List<String>, val sourceLocationTag: String?)
}
