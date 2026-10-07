package org.every.nook.api.application.admin

import org.every.nook.api.application.place.PlaceTagSourcePort
import org.every.nook.api.application.place.PlaceThumbnailProvider
import org.every.nook.api.application.place.StorePlaceTagsUseCase
import org.every.nook.api.application.processing.ParsingResultWriter
import org.slf4j.LoggerFactory

class ProcessPlaceReprocessingUseCase(
    private val jobs: PlaceReprocessingPort,
    private val sources: PlaceReprocessingSourcePort,
    private val thumbnails: PlaceThumbnailProvider,
    private val storeTags: StorePlaceTagsUseCase,
    private val tagSources: PlaceTagSourcePort,
) {
    operator fun invoke(): Boolean {
        val job = jobs.claim() ?: return false
        runCatching { process(job) }.onFailure { exception ->
            logger.error("Place reprocessing failed: placeId={}, attempt={}", job.placeId, job.attempt, exception)
            jobs.fail(job)
        }
        return true
    }

    private fun process(job: ClaimedPlaceReprocessing) {
        val source = sources.find(job.placeId) ?: throw AdminPlaceNotFoundException()
        val supplement = runCatching {
            thumbnails.fetch(
                PlaceThumbnailProvider.Request(source.candidate, failOnProviderError = true),
            )
        }.onFailure { logger.warn("Place supplement refresh failed: placeId={}", job.placeId, it) }
        val changes = mutableListOf<() -> Unit>()
        val tagOutcome = refreshTags(job, source, changes)
        jobs.finish(
            job,
            PlaceReprocessingResult(source, supplement.getOrNull(), supplement.isFailure, tagOutcome, changes),
        )
    }

    private fun refreshTags(
        job: ClaimedPlaceReprocessing,
        source: PlaceReprocessingSource,
        changes: MutableList<() -> Unit>,
    ): PlaceReprocessingOutcome {
        val events = source.tagEvents.filter { tagSources.find(it.postId) != null }
        if (events.isEmpty()) return PlaceReprocessingOutcome.NO_SOURCE
        val extraction = runCatching {
            events.forEach { event ->
                storeTags(event, ParsingResultWriter { changes += it }, setOf(job.placeId))
            }
        }.onFailure { logger.warn("Place tag refresh failed: placeId={}", job.placeId, it) }
        if (extraction.isFailure) {
            changes.clear()
            return PlaceReprocessingOutcome.FAILED
        }
        return PlaceReprocessingOutcome.UPDATED
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ProcessPlaceReprocessingUseCase::class.java)
    }
}
