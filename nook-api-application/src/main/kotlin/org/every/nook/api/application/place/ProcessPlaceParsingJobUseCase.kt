package org.every.nook.api.application.place

import mu.KotlinLogging
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.measure
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ProcessPlaceParsingJobUseCase(
    private val jobPort: PlaceParsingJobPort,
    private val clueExtractor: PlaceClueExtractor,
    private val searchPlaceCandidates: SearchPlaceCandidatesUseCase,
    private val candidateSelector: PlaceCandidateSelector,
    private val retryBackoffs: List<Duration>,
    private val processingTimeout: Duration,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long): Result {
        val job = jobPort.claim(postId, processingTimeout) ?: return Result.Skipped
        val startedAt = clock.instant()
        logger.info { "Place parsing started: postId=${job.postId}, attempt=${job.attempt}" }

        return runCatching {
            val textClues = job.textClues ?: extractClues(job)
            val textResolution = resolveClues(job, textClues)
            val places = if (textResolution.places.isNotEmpty()) {
                textResolution.places
            } else {
                val imageUrls = job.imageUrls.take(MAX_IMAGE_COUNT)
                if (imageUrls.isEmpty()) {
                    terminalFailure(textResolution.failure?.message ?: NO_PLACE_RESOLVED_REASON)
                }
                logger.info {
                    "Place parsing image fallback started: postId=${job.postId}, attempt=${job.attempt}, " +
                        "imageCount=${imageUrls.size}"
                }
                val imageClues = extractClues(job, imageUrls)
                val imageResolution = resolveClues(job, imageClues)
                imageResolution.places.ifEmpty {
                    terminalFailure(
                        imageResolution.failure?.message ?: NO_PLACE_RESOLVED_AFTER_IMAGE_REASON,
                    )
                }
            }
            measure(job, COMPLETE_STAGE) {
                jobPort.complete(job.postId, places)
            }
            val duration = Duration.between(startedAt, clock.instant()).toMillis()
            logger.info {
                "Place parsing completed: postId=${job.postId}, attempt=${job.attempt}, " +
                    "placeCount=${places.size}, durationMs=$duration"
            }
            Result.Completed
        }.getOrElse { exception ->
            handleFailure(job, exception, startedAt)
        }
    }

    private fun extractClues(job: ClaimedPlaceParsingJob, imageUrls: List<String> = emptyList()): List<PlaceClue> =
        measure(job, if (imageUrls.isEmpty()) TEXT_CLUE_STAGE else IMAGE_CLUE_STAGE) {
            clueExtractor.extract(
                PlaceClueExtractor.Request(
                    body = job.body,
                    hashtags = job.hashtags,
                    sourceLocationTag = job.sourceLocationTag,
                    imageUrls = imageUrls,
                ),
            )
        }.also { clues ->
            require(clues.size <= MAX_PLACE_COUNT) { "Too many place clues" }
            logger.info {
                "OpenAI place clues received: postId=${job.postId}, attempt=${job.attempt}, " +
                    "imageCount=${imageUrls.size}, placeCount=${clues.size}, places=$clues"
            }
        }

    private fun resolveClues(job: ClaimedPlaceParsingJob, clues: List<PlaceClue>): ClueResolution {
        var lastFailure: PlaceResolutionException? = null
        val places = clues.mapNotNull { clue ->
            try {
                resolve(job, clue)
            } catch (exception: PlaceResolutionException) {
                lastFailure = exception
                logger.warn {
                    "Place clue skipped: postId=${job.postId}, placeName=${clue.name}, " +
                        "region=${clue.region}, reason=${exception.message}"
                }
                null
            }
        }
        return ClueResolution(places, lastFailure)
    }

    private fun resolve(job: ClaimedPlaceParsingJob, clue: PlaceClue): PlaceCandidate {
        validate(clue)
        val candidates = searchCandidates(job, clue)
        logger.info {
            "Place candidates searched: placeName=${clue.name}, region=${clue.region}, " +
                "queries=${clue.queries}, candidateCount=${candidates.size}"
        }
        val matches = strictMatches(clue, candidates)
        logger.info {
            "Place candidate matching completed: placeName=${clue.name}, region=${clue.region}, " +
                "candidateCount=${candidates.size}, matchCount=${matches.size}, " +
                "candidates=${candidates.take(CANDIDATE_LOG_LIMIT).map { "${it.place.name}|${it.place.address}" }}"
        }

        val resolved = if (matches.size == 1) {
            matches.single().place
        } else {
            if (candidates.isEmpty()) {
                failResolution("No place candidate found: ${clue.name}")
            }
            measure(job, SELECT_STAGE) {
                candidateSelector.select(
                    PlaceCandidateSelector.Request(
                        clue = clue,
                        candidates = candidates,
                    ),
                )
            } ?: failResolution(
                "No place candidate selected: ${clue.name}, strictMatchCount=${matches.size}",
            )
        }
        logger.info {
            "Place resolved: provider=${resolved.provider}, externalPlaceId=${resolved.externalPlaceId}, " +
                "name=${resolved.name}, address=${resolved.address}"
        }
        return resolved
    }

    private fun searchCandidates(
        job: ClaimedPlaceParsingJob,
        clue: PlaceClue,
    ): List<PlaceCandidateSelector.Candidate> {
        val candidatesById = linkedMapOf<Pair<String, String>, PlaceCandidateSelector.Candidate>()
        clue.queries.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .forEach { query ->
                measure(job, SEARCH_STAGE) {
                    searchPlaceCandidates(SearchPlaceCandidatesUseCase.Command(queries = listOf(query)))
                }
                    .forEach { candidate ->
                        val key = candidate.provider to candidate.externalPlaceId
                        val existing = candidatesById[key]
                        candidatesById[key] = PlaceCandidateSelector.Candidate(
                            place = candidate,
                            matchedQueries = existing?.matchedQueries.orEmpty() + query,
                        )
                    }
                if (strictMatches(clue, candidatesById.values).size == 1) {
                    return candidatesById.values.toList()
                }
            }
        return candidatesById.values.toList()
    }

    private fun <T> measure(job: ClaimedPlaceParsingJob, stage: String, action: () -> T): T = metrics.measure(
        flow = PLACE_FLOW,
        stage = stage,
        postId = job.postId,
        attempt = job.attempt,
        clock = clock,
        action = action,
    )

    private fun validate(clue: PlaceClue) {
        if (clue.name.isBlank() || clue.queries.isEmpty() || clue.queries.size > MAX_QUERY_COUNT) {
            failResolution("Invalid place clue")
        }
    }

    private fun failResolution(message: String): Nothing = throw PlaceResolutionException(message)

    private fun handleFailure(job: ClaimedPlaceParsingJob, exception: Throwable, startedAt: Instant): Result {
        val reason = failureReason(exception)
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
        if (exception is TerminalPlaceParsingException) {
            jobPort.fail(job.postId, reason)
            logger.warn {
                "Place parsing failed without retry: postId=${job.postId}, attempt=${job.attempt}, " +
                    "durationMs=$duration, reason=$reason"
            }
            return Result.Failed
        }

        val backoff = retryBackoffs.getOrNull(job.attempt - 1)
        if (backoff != null) {
            val nextAttemptAt = clock.instant().plus(backoff)
            jobPort.retry(job.postId, nextAttemptAt, reason)
            logger.warn(exception) {
                "Place parsing retry scheduled: postId=${job.postId}, attempt=${job.attempt}, " +
                    "nextAttemptAt=$nextAttemptAt, durationMs=$duration, reason=$reason"
            }
            return Result.Retry(nextAttemptAt)
        }

        jobPort.fail(job.postId, reason)
        logger.error(exception) {
            "Place parsing failed permanently: postId=${job.postId}, attempt=${job.attempt}, " +
                "durationMs=$duration, reason=$reason"
        }
        return Result.Failed
    }

    private fun failureReason(exception: Throwable): String = exception.message.orEmpty()
        .ifBlank { DEFAULT_FAILURE_REASON }
        .take(MAX_FAILURE_REASON_LENGTH)

    private fun terminalFailure(message: String): Nothing = throw TerminalPlaceParsingException(message)

    sealed interface Result {
        data object Completed : Result

        data class Retry(val nextAttemptAt: Instant) : Result

        data object Failed : Result

        data object Skipped : Result
    }

    private companion object {
        val logger = KotlinLogging.logger {}

        const val MAX_PLACE_COUNT = 10
        const val MAX_QUERY_COUNT = 4
        const val MAX_IMAGE_COUNT = 20
        const val CANDIDATE_LOG_LIMIT = 5
        const val MAX_FAILURE_REASON_LENGTH = 500
        const val DEFAULT_FAILURE_REASON = "Place parsing failed"
        const val NO_PLACE_RESOLVED_REASON = "No place could be resolved from text"
        const val NO_PLACE_RESOLVED_AFTER_IMAGE_REASON = "No place could be resolved after image analysis"
        const val PLACE_FLOW = "place"
        const val TEXT_CLUE_STAGE = "clue-text"
        const val IMAGE_CLUE_STAGE = "clue-image"
        const val SEARCH_STAGE = "search"
        const val SELECT_STAGE = "select"
        const val COMPLETE_STAGE = "complete"
    }

    private class PlaceResolutionException(message: String) : IllegalStateException(message)

    private class TerminalPlaceParsingException(message: String) : IllegalStateException(message)

    private data class ClueResolution(val places: List<PlaceCandidate>, val failure: PlaceResolutionException?)
}

private fun strictMatches(
    clue: PlaceClue,
    candidates: Collection<PlaceCandidateSelector.Candidate>,
): List<PlaceCandidateSelector.Candidate> {
    val normalizedName = clue.name.normalize()
    val normalizedRegion = clue.region?.normalize()?.takeIf(String::isNotEmpty)
    return candidates.filter { candidate ->
        candidate.place.name.normalize() == normalizedName &&
            (normalizedRegion == null || candidate.place.address.normalize().contains(normalizedRegion))
    }
}

private fun String.normalize(): String = lowercase().filterNot(Char::isWhitespace)
