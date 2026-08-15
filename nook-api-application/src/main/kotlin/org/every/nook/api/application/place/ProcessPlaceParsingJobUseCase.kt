package org.every.nook.api.application.place

import mu.KotlinLogging
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.error
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.measure
import org.every.nook.api.application.processing.warn
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ProcessPlaceParsingJobUseCase(
    private val jobPort: PlaceParsingJobPort,
    private val imageTextExtractor: ImageTextExtractor,
    private val clueExtractor: PlaceClueExtractor,
    private val searchPlaceCandidates: SearchPlaceCandidatesUseCase,
    private val candidateSelector: PlaceCandidateSelector,
    private val retryBackoffs: List<Duration>,
    private val processingTimeout: Duration,
    private val imageReadinessPort: PlaceImageReadinessPort = PlaceImageReadinessPort { true },
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val clock: Clock = Clock.systemUTC(),
) {
    operator fun invoke(postId: Long): Result {
        val job = jobPort.claim(postId, processingTimeout) ?: return Result.Skipped
        val startedAt = clock.instant()
        eventLogger.info(job.event("place.job.claimed", JOB_STAGE, SUCCESS_OUTCOME))
        logger.info { "Place parsing started: postId=${job.postId}, attempt=${job.attempt}" }

        return runCatching { process(job, startedAt) }.getOrElse { exception ->
            handleFailure(job, exception, startedAt)
        }
    }

    private fun process(job: ClaimedPlaceParsingJob, startedAt: Instant): Result {
        val expectedPlaceCount = expectedPlaceCount(job.body)
        val textClues = (job.textClues ?: extractClues(job)).filter { clue ->
            clue.isGroundedIn(job).also { grounded ->
                if (!grounded) {
                    logger.warn {
                        "Ungrounded text place clue skipped: postId=${job.postId}, attempt=${job.attempt}, " +
                            "placeName=${clue.name}, region=${clue.region}, queries=${clue.queries}"
                    }
                }
            }
        }
        val textResolution = resolveClues(job, textClues)
        logOcrDecision(eventLogger, job, textResolution.places.size, expectedPlaceCount)
        val imageResolution = resolveImageClues(job, textResolution.places.size, expectedPlaceCount)
        val places = (textResolution.places + imageResolution?.places.orEmpty())
            .distinctBy { it.provider to it.externalPlaceId }
        if (places.isEmpty()) {
            val failure = imageResolution?.failure ?: textResolution.failure
            terminalFailure(
                failure?.message ?: if (imageResolution == null) {
                    NO_PLACE_RESOLVED_REASON
                } else {
                    NO_PLACE_RESOLVED_AFTER_IMAGE_REASON
                },
            )
        }
        measure(job, COMPLETE_STAGE) {
            jobPort.complete(job.postId, places)
        }
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
        logger.info {
            "Place parsing completed: postId=${job.postId}, attempt=${job.attempt}, " +
                "placeCount=${places.size}, durationMs=$duration"
        }
        eventLogger.info(
            job.event(
                "place.job.completed",
                JOB_STAGE,
                SUCCESS_OUTCOME,
                duration,
                mapOf("place.resolved_count" to places.size),
            ),
        )
        return Result.Completed
    }

    private fun resolveImageClues(
        job: ClaimedPlaceParsingJob,
        textPlaceCount: Int,
        expectedPlaceCount: Int?,
    ): ClueResolution? {
        val images = job.imageUrls.take(MAX_IMAGE_COUNT).mapIndexed { index, imageUrl ->
            ImageTextExtractor.ImageInput(imageIndex = index + 1, imageUrl = imageUrl)
        }
        if (images.isEmpty() || !requiresImageAnalysis(textPlaceCount, expectedPlaceCount)) {
            return null
        }
        if (!imageReadinessPort.areImageUrlsReadyForOcr(job.postId)) {
            logger.info {
                "Place parsing image fallback delayed: postId=${job.postId}, attempt=${job.attempt}, " +
                    "reason=$OCR_IMAGE_STORAGE_PENDING_REASON, imageCount=${images.size}"
            }
            error(OCR_IMAGE_STORAGE_PENDING_REASON)
        }
        logger.info {
            "Place parsing image fallback started: postId=${job.postId}, attempt=${job.attempt}, " +
                "imageCount=${images.size}, textPlaceCount=$textPlaceCount, " +
                "expectedPlaceCount=$expectedPlaceCount"
        }
        val transcripts = job.imageTranscripts ?: measure(job, IMAGE_TRANSCRIPT_STAGE) {
            extractImageTranscripts(imageTextExtractor, images)
        }.also { extracted ->
            logger.info {
                "Image transcripts received: postId=${job.postId}, attempt=${job.attempt}, " +
                    "imageCount=${images.size}, transcriptCount=${extracted.size}"
            }
            jobPort.storeImageTranscripts(job.postId, extracted)
        }
        val primaryImageClues = extractClues(job, transcripts)
            .filterGroundedImageClues(images.size, job.postId, job.attempt, recovered = false)
        val recoveredImageClues = ImageClueRecallRecovery(
            retranscribe = { recoveryImages ->
                measure(job, IMAGE_TRANSCRIPT_STAGE) {
                    extractImageTranscripts(imageTextExtractor, recoveryImages)
                }
            },
            storeTranscripts = { recovered -> jobPort.storeImageTranscripts(job.postId, recovered) },
            extractClues = { recoveryTranscripts -> extractClues(job, recoveryTranscripts) },
        ).recover(
            ImageClueRecallRecovery.Request(
                postId = job.postId,
                attempt = job.attempt,
                images = images,
                transcripts = transcripts,
                primaryClues = primaryImageClues,
                knownPlaceCount = textPlaceCount,
                expectedPlaceCount = expectedPlaceCount,
            ),
        ).filterGroundedImageClues(images.size, job.postId, job.attempt, recovered = true)
        val imageClues = primaryImageClues + recoveredImageClues
        return resolveClues(job, imageClues)
    }

    private fun extractClues(
        job: ClaimedPlaceParsingJob,
        imageTranscripts: List<ImageTranscript> = emptyList(),
    ): List<PlaceClue> = measure(job, if (imageTranscripts.isEmpty()) TEXT_CLUE_STAGE else IMAGE_CLUE_STAGE) {
        clueExtractor.extract(
            PlaceClueExtractor.Request(
                body = job.body,
                hashtags = job.hashtags,
                sourceLocationTag = job.sourceLocationTag,
                imageTranscripts = imageTranscripts,
            ),
        )
    }.also { clues ->
        require(clues.size <= MAX_PLACE_COUNT) { "Too many place clues" }
        logger.info {
            "OpenAI place clues received: postId=${job.postId}, attempt=${job.attempt}, " +
                "transcriptCount=${imageTranscripts.size}, placeCount=${clues.size}, places=$clues"
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
        if (clue.name.isBlank() || clue.queries.isEmpty() || clue.queries.size > MAX_QUERY_COUNT) {
            failResolution("Invalid place clue")
        }
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
        if (!clue.isSupportedBy(resolved)) {
            failResolution("Selected place is not grounded in image evidence: ${clue.name}")
        }
        eventLogger.info(
            job.event(
                "place.candidate.selected",
                SELECT_STAGE,
                SUCCESS_OUTCOME,
                fields = mapOf(
                    "provider.name" to resolved.provider,
                    "place.external_id" to resolved.externalPlaceId,
                    "place.selection_method" to if (matches.size == 1) "strict_match" else "openai",
                    "place.candidate_count" to candidates.size,
                    "place.strict_match_count" to matches.size,
                ),
            ),
        )
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

    private fun failResolution(message: String): Nothing = throw PlaceResolutionException(message)

    private fun handleFailure(job: ClaimedPlaceParsingJob, exception: Throwable, startedAt: Instant): Result {
        val reason = placeFailureReason(exception)
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
        if (exception is TerminalPlaceParsingException) {
            jobPort.fail(job.postId, reason)
            eventLogger.warn(
                job.event("place.job.failed", JOB_STAGE, FAILURE_OUTCOME, duration, failureFields(exception, reason)),
                exception,
            )
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
            eventLogger.warn(
                job.event(
                    "place.job.retry_scheduled",
                    JOB_STAGE,
                    FAILURE_OUTCOME,
                    duration,
                    failureFields(exception, reason) + ("retry.next_attempt_at" to nextAttemptAt),
                ),
                exception,
            )
            logger.warn(exception) {
                "Place parsing retry scheduled: postId=${job.postId}, attempt=${job.attempt}, " +
                    "nextAttemptAt=$nextAttemptAt, durationMs=$duration, reason=$reason"
            }
            return Result.Retry(nextAttemptAt)
        }

        jobPort.fail(job.postId, reason)
        eventLogger.error(
            job.event("place.job.failed", JOB_STAGE, FAILURE_OUTCOME, duration, failureFields(exception, reason)),
            exception,
        )
        logger.error(exception) {
            "Place parsing failed permanently: postId=${job.postId}, attempt=${job.attempt}, " +
                "durationMs=$duration, reason=$reason"
        }
        return Result.Failed
    }

    private fun terminalFailure(message: String): Nothing = throw TerminalPlaceParsingException(message)

    sealed interface Result {
        data object Completed : Result

        data class Retry(val nextAttemptAt: Instant) : Result

        data object Failed : Result

        data object Skipped : Result
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        val eventLogger = LoggerFactory.getLogger(ProcessPlaceParsingJobUseCase::class.java)

        const val MAX_PLACE_COUNT = 60
        const val MAX_QUERY_COUNT = 4
        const val MAX_IMAGE_COUNT = 20
        const val CANDIDATE_LOG_LIMIT = 5
        const val NO_PLACE_RESOLVED_REASON = "No place could be resolved from text"
        const val NO_PLACE_RESOLVED_AFTER_IMAGE_REASON = "No place could be resolved after image analysis"
        const val OCR_IMAGE_STORAGE_PENDING_REASON = "OCR image storage is not ready"
        const val PLACE_FLOW = "place"
        const val TEXT_CLUE_STAGE = "clue-text"
        const val IMAGE_TRANSCRIPT_STAGE = "image-transcript"
        const val IMAGE_CLUE_STAGE = "clue-image"
        const val SEARCH_STAGE = "search"
        const val SELECT_STAGE = "select"
        const val COMPLETE_STAGE = "complete"
        const val JOB_STAGE = "job"
        const val OCR_STAGE = "ocr"
        const val SUCCESS_OUTCOME = "success"
        const val FAILURE_OUTCOME = "failure"
    }

    private class PlaceResolutionException(message: String) : IllegalStateException(message)

    private class TerminalPlaceParsingException(message: String) : IllegalStateException(message)

    private data class ClueResolution(val places: List<PlaceCandidate>, val failure: PlaceResolutionException?)
}

private fun logOcrDecision(
    logger: org.slf4j.Logger,
    job: ClaimedPlaceParsingJob,
    textPlaceCount: Int,
    expectedPlaceCount: Int?,
) {
    logger.info(
        job.event(
            "place.ocr.decision",
            "ocr",
            "success",
            fields = mapOf(
                "ocr.required" to requiresImageAnalysis(textPlaceCount, expectedPlaceCount),
                "ocr.reason" to ocrReason(textPlaceCount, expectedPlaceCount, job.imageUrls.isEmpty()),
                "place.text_resolved_count" to textPlaceCount,
                "place.expected_count" to expectedPlaceCount,
                "content.image_count" to job.imageUrls.size,
            ),
        ),
    )
}

private fun PlaceClue.isGroundedIn(job: ClaimedPlaceParsingJob): Boolean {
    val sources = buildList {
        job.body?.let(::add)
        addAll(job.hashtags)
        job.sourceLocationTag?.let(::add)
    }.map(String::groundingKey)
    return (listOf(name) + queries)
        .asSequence()
        .map(String::groundingKey)
        .filter { it.length >= MIN_GROUNDING_KEY_LENGTH }
        .any { clueText -> sources.any { source -> source.contains(clueText) } }
}

private fun requiresImageAnalysis(textPlaceCount: Int, expectedPlaceCount: Int?): Boolean =
    textPlaceCount == 0 || expectedPlaceCount?.let { textPlaceCount < it } == true

private fun ocrReason(textPlaceCount: Int, expectedPlaceCount: Int?, imagesEmpty: Boolean): String = when {
    imagesEmpty -> "no_images"
    textPlaceCount == 0 -> "no_text_place_resolved"
    expectedPlaceCount != null && textPlaceCount < expectedPlaceCount -> "expected_place_count_shortfall"
    else -> "text_places_sufficient"
}

private fun expectedPlaceCount(body: String?): Int? = body?.let { content ->
    EXPECTED_PLACE_COUNT_PATTERN.findAll(content)
        .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
        .filter { count -> count in MIN_EXPECTED_PLACE_COUNT..MAX_EXPECTED_PLACE_COUNT }
        .maxOrNull()
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

private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)

private const val MIN_GROUNDING_KEY_LENGTH = 2
private const val MIN_EXPECTED_PLACE_COUNT = 2
private const val MAX_EXPECTED_PLACE_COUNT = 80
private val EXPECTED_PLACE_COUNT_PATTERN = Regex("(?<!\\d)(\\d{1,2})\\s*(?:곳|선|군데)")

private fun PlaceClue.isSupportedBy(candidate: PlaceCandidate): Boolean {
    if (evidence.isEmpty()) return true
    val candidateName = candidate.name.groundingKey()
    val clueName = name.groundingKey()
    val hasCompatibleName = candidateName == clueName ||
        candidateName.isCompatibleName(clueName) ||
        queries.asSequence().map(String::groundingKey).any(candidateName::isCompatibleName)
    val candidateAddress = candidate.address.groundingKey()
    val candidateAddressKeys = candidate.address.addressKeys()
    val hasCompatibleEvidence = evidence.asSequence()
        .map(PlaceClueEvidence::evidenceText)
        .any { evidenceText ->
            val normalizedEvidence = evidenceText.groundingKey()
            val containsName = candidateName.length >= MIN_GROUNDING_KEY_LENGTH &&
                normalizedEvidence.contains(candidateName)
            val containsAddress = candidateAddress.length >= MIN_ADDRESS_GROUNDING_KEY_LENGTH &&
                normalizedEvidence.contains(candidateAddress)
            containsName || containsAddress ||
                candidateAddressKeys.any { it in evidenceText.addressKeys() }
        }
    return hasCompatibleName || hasCompatibleEvidence
}

private fun String.isCompatibleName(other: String): Boolean = length >= MIN_NAME_COMPATIBILITY_KEY_LENGTH &&
    other.length >= MIN_NAME_COMPATIBILITY_KEY_LENGTH &&
    (contains(other) || other.contains(this))

private fun String.addressKeys(): Set<String> = ADDRESS_KEY_PATTERN.findAll(this).map { match ->
    match.groupValues.drop(1).joinToString(separator = "").groundingKey()
}.toSet()

private const val MIN_NAME_COMPATIBILITY_KEY_LENGTH = 3
private const val MIN_ADDRESS_GROUNDING_KEY_LENGTH = 6
private val ADDRESS_KEY_PATTERN = Regex(
    "([가-힣A-Za-z0-9]+(?:대로|로|길|동|읍|면|리))\\s*(\\d+(?:-\\d+)?)",
)
