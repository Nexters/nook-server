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

    fun complete(jobId: Long, attempt: Int): Boolean

    fun retry(jobId: Long, attempt: Int, availableAt: Instant, reason: String): Boolean

    fun fail(jobId: Long, attempt: Int, reason: String): Boolean

    fun writeResult(jobId: Long, attempt: Int, change: () -> Unit): Boolean
}

sealed interface ClaimedParsingFollowUpJob {
    val id: Long
    val attempt: Int
    val retryAttempt: Int

    data class Media(
        override val id: Long,
        override val attempt: Int,
        val event: PostMediaFollowUp,
        override val retryAttempt: Int = attempt,
    ) : ClaimedParsingFollowUpJob

    data class Thumbnails(
        override val id: Long,
        override val attempt: Int,
        val event: PlaceThumbnailsFollowUp,
        override val retryAttempt: Int = attempt,
    ) : ClaimedParsingFollowUpJob

    data class Tags(
        override val id: Long,
        override val attempt: Int,
        val event: PlaceTagsFollowUp,
        override val retryAttempt: Int = attempt,
    ) : ClaimedParsingFollowUpJob
}
