package org.every.nook.api.application.processing

import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import org.every.nook.api.application.place.PlaceThumbnailsRequestedEvent
import org.every.nook.api.application.post.PostMediaStorageRequestedEvent
import java.time.Duration
import java.time.Instant

typealias PostMediaFollowUp = PostMediaStorageRequestedEvent
typealias PlaceThumbnailsFollowUp = PlaceThumbnailsRequestedEvent
typealias PlaceTagsFollowUp = PlaceTagsRequestedEvent

interface ParsingFollowUpJobPort {
    fun enqueue(event: PostMediaStorageRequestedEvent)

    fun enqueue(event: PlaceThumbnailsRequestedEvent)

    fun enqueue(event: PlaceTagsRequestedEvent)

    fun claim(limit: Int, processingTimeout: Duration): List<ClaimedParsingFollowUpJob>

    fun complete(jobId: Long)

    fun retry(jobId: Long, availableAt: Instant, reason: String)

    fun fail(jobId: Long, reason: String)
}

sealed interface ClaimedParsingFollowUpJob {
    val id: Long
    val attempt: Int

    data class Media(override val id: Long, override val attempt: Int, val event: PostMediaFollowUp) :
        ClaimedParsingFollowUpJob

    data class Thumbnails(override val id: Long, override val attempt: Int, val event: PlaceThumbnailsFollowUp) :
        ClaimedParsingFollowUpJob

    data class Tags(override val id: Long, override val attempt: Int, val event: PlaceTagsFollowUp) :
        ClaimedParsingFollowUpJob
}
