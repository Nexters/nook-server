package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.admin.AdminAuditLogPort
import org.every.nook.api.application.admin.AdminPlaceNotFoundException
import org.every.nook.api.application.admin.ClaimedPlaceReprocessing
import org.every.nook.api.application.admin.PlaceReprocessingActiveException
import org.every.nook.api.application.admin.PlaceReprocessingJob
import org.every.nook.api.application.admin.PlaceReprocessingOutcome
import org.every.nook.api.application.admin.PlaceReprocessingPort
import org.every.nook.api.application.admin.PlaceReprocessingResult
import org.every.nook.api.application.admin.PlaceReprocessingStatus
import org.every.nook.api.application.admin.RequestPlaceReprocessingCommand
import org.every.nook.api.domain.place.PlaceThumbnailParsingStatus
import org.every.nook.api.infrastructure.persistence.place.PlaceEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Component
class PlaceReprocessingAdapter(
    private val jobs: PlaceReprocessingJobJpaRepository,
    private val places: PlaceJpaRepository,
    private val audit: AdminAuditLogPort,
    private val protection: PlaceReprocessingProtection,
    private val clock: Clock = Clock.systemUTC(),
) : PlaceReprocessingPort {
    @Transactional
    override fun enqueue(command: RequestPlaceReprocessingCommand): PlaceReprocessingJob {
        places.findByIdForUpdate(command.placeId) ?: throw AdminPlaceNotFoundException()
        val existing = jobs.findForUpdate(command.placeId)
        if (existing != null && existing.status.active()) throw PlaceReprocessingActiveException()
        val job = existing ?: PlaceReprocessingJobEntity(command.placeId)
        job.status = PlaceReprocessingStatus.PENDING
        job.photos = PlaceReprocessingOutcome.PENDING
        job.tags = PlaceReprocessingOutcome.PENDING
        val saved = jobs.saveAndFlush(job)
        audit.append(
            AdminAuditLogPort.Entry(
                actor = command.actor,
                action = "PLACE_REPROCESSING_REQUESTED",
                targetType = "PLACE",
                targetId = command.placeId.toString(),
                reason = command.reason,
                beforeValue = null,
                afterValue = """{"status":"PENDING","attempt":${job.attempt}}""",
                requestId = command.requestId,
            ),
        )
        return saved.toView()
    }

    @Transactional(readOnly = true)
    override fun find(placeId: Long): PlaceReprocessingJob? {
        if (!places.existsById(placeId)) throw AdminPlaceNotFoundException()
        return jobs.findById(placeId).orElse(null)?.toView()
    }

    @Transactional
    override fun claim(): ClaimedPlaceReprocessing? {
        val cutoff = clock.instant().minus(PROCESSING_TIMEOUT)
        val placeId = jobs.findClaimableIds(cutoff, PageRequest.of(0, 1)).firstOrNull() ?: return null
        val job = jobs.findForUpdate(placeId) ?: return null
        return when {
            job.status == PlaceReprocessingStatus.PENDING -> {
                job.status = PlaceReprocessingStatus.PROCESSING
                job.attempt += 1
                ClaimedPlaceReprocessing(placeId, job.attempt)
            }

            job.status == PlaceReprocessingStatus.PROCESSING && job.updatedAt <= cutoff -> {
                // End an abandoned execution; manual retry is explicit and old results are fenced out.
                markFailed(job)
                null
            }

            else -> null
        }
    }

    @Transactional
    override fun finish(job: ClaimedPlaceReprocessing, result: PlaceReprocessingResult): Boolean {
        val place = places.findByIdForUpdate(job.placeId) ?: return fail(job)
        val stored = jobs.findForUpdate(job.placeId)?.takeIf { it.current(job) } ?: return false
        if (place.name != result.source.candidate.name || place.address != result.source.candidate.address) {
            markFailed(stored)
        } else {
            applyResult(place, stored, result)
        }
        return true
    }

    @Transactional
    override fun fail(job: ClaimedPlaceReprocessing): Boolean {
        val stored = jobs.findForUpdate(job.placeId)?.takeIf { it.current(job) } ?: return false
        markFailed(stored)
        return true
    }

    private fun applyResult(place: PlaceEntity, job: PlaceReprocessingJobEntity, result: PlaceReprocessingResult) {
        val protected = protectedFields(place, result.source, protection.fields(job.placeId))
        job.photos = applyPhotos(place, result, protected.photos)
        job.tags = if (protected.tags) {
            PlaceReprocessingOutcome.PRESERVED
        } else {
            result.tagChanges.forEach { it() }
            if (result.tagOutcome == PlaceReprocessingOutcome.UPDATED && place.representativeTags.isEmpty()) {
                PlaceReprocessingOutcome.NO_DATA
            } else {
                result.tagOutcome
            }
        }
        result.supplement?.googlePlaceId?.let { place.googlePlaceId = it }
        place.thumbnailParsingStatus = if (place.thumbnailUrl != null || place.photoUrls.isNotEmpty()) {
            PlaceThumbnailParsingStatus.COMPLETED
        } else {
            PlaceThumbnailParsingStatus.FAILED
        }
        job.status = overallStatus(listOf(job.photos, job.tags))
    }

    private fun applyPhotos(
        place: PlaceEntity,
        result: PlaceReprocessingResult,
        protected: Boolean,
    ): PlaceReprocessingOutcome = when {
        protected -> PlaceReprocessingOutcome.PRESERVED

        result.supplementFailed -> PlaceReprocessingOutcome.FAILED

        result.supplement?.photoUrls.isNullOrEmpty() -> PlaceReprocessingOutcome.NO_DATA

        else -> {
            place.photoUrls = requireNotNull(result.supplement).photoUrls
            place.thumbnailUrl = place.photoUrls.first()
            PlaceReprocessingOutcome.UPDATED
        }
    }

    private fun markFailed(job: PlaceReprocessingJobEntity) {
        job.status = PlaceReprocessingStatus.FAILED
        job.photos = PlaceReprocessingOutcome.FAILED
        job.tags = PlaceReprocessingOutcome.FAILED
    }

    private companion object {
        val PROCESSING_TIMEOUT: Duration = Duration.ofMinutes(15)
    }
}

internal fun overallStatus(results: List<PlaceReprocessingOutcome>): PlaceReprocessingStatus = when {
    results.all { it == PlaceReprocessingOutcome.FAILED } -> PlaceReprocessingStatus.FAILED

    results.all { it == PlaceReprocessingOutcome.UPDATED || it == PlaceReprocessingOutcome.PRESERVED } ->
        PlaceReprocessingStatus.COMPLETED

    else -> PlaceReprocessingStatus.PARTIAL
}

private fun PlaceReprocessingStatus.active(): Boolean =
    this == PlaceReprocessingStatus.PENDING || this == PlaceReprocessingStatus.PROCESSING

private fun PlaceReprocessingJobEntity.current(job: ClaimedPlaceReprocessing): Boolean =
    status == PlaceReprocessingStatus.PROCESSING && attempt == job.attempt

private fun PlaceReprocessingJobEntity.toView() =
    PlaceReprocessingJob(placeId, status, attempt, photos, tags, updatedAt)
