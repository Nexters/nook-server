package org.every.nook.api.application.post.port

import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostSource

fun interface FindExistingPostPort {
    fun find(source: PostSource): ExistingPost?
}

data class ExistingPost(val placeParsingStatus: PlaceParsingStatus)
