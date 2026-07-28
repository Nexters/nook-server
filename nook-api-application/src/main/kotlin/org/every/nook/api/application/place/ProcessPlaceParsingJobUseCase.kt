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
            require(clues.size <= MAX_PLACE_COUNT) { "Too many place clues" }
            logger.info {
                "OpenAI place clues received: postId=${job.postId}, attempt=${job.attempt}, " +
                    "placeCount=${clues.size}, places=$clues"
            }
            val places = clues.map(::resolve)
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
        require(clue.name.isNotBlank() && clue.queries.isNotEmpty() && clue.queries.size <= MAX_QUERY_COUNT) {
            "Invalid place clue"
        }
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

        val resolved = matches.singleOrNull()
            ?: error("Place could not be uniquely identified: ${clue.name}")
        logger.info {
            "Place resolved: provider=${resolved.provider}, externalPlaceId=${resolved.externalPlaceId}, " +
                "name=${resolved.name}, address=${resolved.address}"
        }
        return resolved
    }

    private fun handleFailure(job: ClaimedPlaceParsingJob, exception: Throwable, startedAt: Instant): Result {
        val reason = exception.message.orEmpty()
            .ifBlank { DEFAULT_FAILURE_REASON }
            .take(MAX_FAILURE_REASON_LENGTH)
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
        val backoff = retryBackoffs.getOrNull(job.attempt - 1)
        if (backoff != null) {
            val nextAttemptAt = clock.instant().plus(backoff)
            jobPort.retry(job.postId, nextAttemptAt)
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
        const val MAX_QUERY_COUNT = 3
        const val MAX_FAILURE_REASON_LENGTH = 500
        const val DEFAULT_FAILURE_REASON = "Place parsing failed"
    }
}
