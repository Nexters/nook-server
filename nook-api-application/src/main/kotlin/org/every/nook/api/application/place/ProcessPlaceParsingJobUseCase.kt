package org.every.nook.api.application.place

import mu.KotlinLogging
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ProcessPlaceParsingJobUseCase(
    private val jobPort: PlaceParsingJobPort,
    private val clueExtractor: PlaceClueExtractor,
    private val searchPlaceCandidates: SearchPlaceCandidatesUseCase,
    private val retryBackoffs: List<Duration>,
    private val processingTimeout: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long): Result {
        val job = jobPort.claim(postId, processingTimeout) ?: return Result.Skipped
        val startedAt = clock.instant()
        logger.info { "Place parsing started: postId=${job.postId}, attempt=${job.attempt}" }

        return runCatching {
            val clues = clueExtractor.extract(
                PlaceClueExtractor.Request(
                    body = job.body,
                    hashtags = job.hashtags,
                    sourceLocationTag = job.sourceLocationTag,
                ),
            )
            check(clues.isNotEmpty()) { NO_PLACE_CLUE_REASON }
            require(clues.size <= MAX_PLACE_COUNT) { "Too many place clues" }
            logger.info {
                "OpenAI place clues received: postId=${job.postId}, attempt=${job.attempt}, " +
                    "placeCount=${clues.size}, places=$clues"
            }
            var lastResolutionFailure: PlaceResolutionException? = null
            val places = clues.mapNotNull { clue ->
                try {
                    resolve(clue)
                } catch (exception: PlaceResolutionException) {
                    lastResolutionFailure = exception
                    logger.warn {
                        "Place clue skipped: postId=${job.postId}, placeName=${clue.name}, " +
                            "region=${clue.region}, reason=${exception.message}"
                    }
                    null
                }
            }
            if (places.isEmpty()) {
                throw requireNotNull(lastResolutionFailure)
            }
            jobPort.complete(job.postId, places)
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

    private fun resolve(clue: PlaceClue): PlaceCandidate {
        validate(clue)
        val candidates = searchPlaceCandidates(
            SearchPlaceCandidatesUseCase.Command(queries = clue.queries),
        )
        logger.info {
            "Place candidates searched: placeName=${clue.name}, region=${clue.region}, " +
                "queries=${clue.queries}, candidateCount=${candidates.size}"
        }
        val normalizedName = clue.name.normalize()
        val normalizedRegion = clue.region?.normalize()?.takeIf(String::isNotEmpty)
        val matches = candidates.filter { candidate ->
            candidate.name.normalize() == normalizedName &&
                (normalizedRegion == null || candidate.address.normalize().contains(normalizedRegion))
        }
        logger.info {
            "Place candidate matching completed: placeName=${clue.name}, region=${clue.region}, " +
                "candidateCount=${candidates.size}, matchCount=${matches.size}, " +
                "candidates=${candidates.take(CANDIDATE_LOG_LIMIT).map { "${it.name}|${it.address}" }}"
        }

        val resolved = when (matches.size) {
            0 -> failResolution("No place candidate matched: ${clue.name}")

            1 -> matches.single()

            else -> failResolution(
                "Multiple place candidates matched: ${clue.name}, matchCount=${matches.size}",
            )
        }
        logger.info {
            "Place resolved: provider=${resolved.provider}, externalPlaceId=${resolved.externalPlaceId}, " +
                "name=${resolved.name}, address=${resolved.address}"
        }
        return resolved
    }

    private fun validate(clue: PlaceClue) {
        if (clue.name.isBlank() || clue.queries.isEmpty() || clue.queries.size > MAX_QUERY_COUNT) {
            failResolution("Invalid place clue")
        }
    }

    private fun failResolution(message: String): Nothing = throw PlaceResolutionException(message)

    private fun handleFailure(job: ClaimedPlaceParsingJob, exception: Throwable, startedAt: Instant): Result {
        val reason = exception.message.orEmpty()
            .ifBlank { DEFAULT_FAILURE_REASON }
            .take(MAX_FAILURE_REASON_LENGTH)
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
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

    private fun String.normalize(): String = lowercase().filterNot(Char::isWhitespace)

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
        const val CANDIDATE_LOG_LIMIT = 5
        const val MAX_FAILURE_REASON_LENGTH = 500
        const val DEFAULT_FAILURE_REASON = "Place parsing failed"
        const val NO_PLACE_CLUE_REASON = "No place clue was extracted"
    }

    private class PlaceResolutionException(message: String) : IllegalStateException(message)
}
