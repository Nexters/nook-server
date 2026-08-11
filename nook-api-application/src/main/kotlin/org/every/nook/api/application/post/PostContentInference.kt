package org.every.nook.api.application.post

import org.every.nook.api.application.place.PlaceClue

fun interface PostContentInference {
    fun infer(request: Request): Inference

    data class Request(val body: String?, val hashtags: List<String>, val sourceLocationTag: String?)

    data class Inference(val title: String, val placeClues: List<PlaceClue>)
}
