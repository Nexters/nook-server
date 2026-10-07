package org.every.nook.api.application.admin

import org.every.nook.api.application.place.PlaceCandidate
import org.every.nook.api.application.place.PlaceSupplement
import org.every.nook.api.application.place.PlaceTagsRequestedEvent
import java.time.Instant

enum class PlaceReprocessingStatus { PENDING, PROCESSING, COMPLETED, PARTIAL, FAILED }

enum class PlaceReprocessingOutcome { PENDING, UPDATED, PRESERVED, NO_DATA, NO_SOURCE, FAILED }

data class PlaceReprocessingJob(
    val placeId: Long,
    val status: PlaceReprocessingStatus,
    val attempt: Int,
    val photos: PlaceReprocessingOutcome,
    val tags: PlaceReprocessingOutcome,
    val updatedAt: Instant,
)

data class RequestPlaceReprocessingCommand(
    val placeId: Long,
    val actor: AdminActor,
    val reason: String,
    val requestId: String?,
)

data class ClaimedPlaceReprocessing(val placeId: Long, val attempt: Int)

data class PlaceReprocessingSource(
    val candidate: PlaceCandidate,
    val thumbnailUrl: String?,
    val photoUrls: List<String>,
    val representativeTags: List<String>,
    val tagEvents: List<PlaceTagsRequestedEvent>,
)

data class PlaceReprocessingResult(
    val source: PlaceReprocessingSource,
    val supplement: PlaceSupplement?,
    val supplementFailed: Boolean,
    val tagOutcome: PlaceReprocessingOutcome,
    val tagChanges: List<() -> Unit>,
)

interface PlaceReprocessingPort {
    fun enqueue(command: RequestPlaceReprocessingCommand): PlaceReprocessingJob

    fun find(placeId: Long): PlaceReprocessingJob?

    fun claim(): ClaimedPlaceReprocessing?

    fun finish(job: ClaimedPlaceReprocessing, result: PlaceReprocessingResult): Boolean

    fun fail(job: ClaimedPlaceReprocessing): Boolean
}

fun interface PlaceReprocessingSourcePort {
    fun find(placeId: Long): PlaceReprocessingSource?
}

class RequestPlaceReprocessingUseCase(private val port: PlaceReprocessingPort) {
    operator fun invoke(command: RequestPlaceReprocessingCommand): PlaceReprocessingJob {
        require(command.placeId > 0)
        require(command.reason.isNotBlank() && command.reason.length <= MAX_REASON_LENGTH)
        return port.enqueue(command.copy(reason = command.reason.trim()))
    }

    private companion object {
        const val MAX_REASON_LENGTH = 500
    }
}

class GetPlaceReprocessingUseCase(private val port: PlaceReprocessingPort) {
    operator fun invoke(placeId: Long): PlaceReprocessingJob? {
        require(placeId > 0)
        return port.find(placeId)
    }
}
