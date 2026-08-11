package org.every.nook.api.application.place

fun interface ImageTextExtractor {
    fun extract(request: Request): List<ImageTranscript>

    data class Request(val images: List<ImageInput>)

    data class ImageInput(val imageIndex: Int, val imageUrl: String)
}
