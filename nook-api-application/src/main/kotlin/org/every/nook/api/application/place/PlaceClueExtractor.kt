package org.every.nook.api.application.place

fun interface PlaceClueExtractor {
    fun extract(request: Request): List<PlaceClue>

    data class Request(val body: String?, val hashtags: List<String>, val sourceLocationTag: String?)
}
