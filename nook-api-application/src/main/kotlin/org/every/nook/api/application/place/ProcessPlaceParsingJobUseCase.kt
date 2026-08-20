package org.every.nook.api.application.place

import mu.KotlinLogging
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.ParsingProgressStage
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
    private val imageUrlPort: PlaceImageUrlPort = PlaceImageUrlPort { emptyList() },
    private val clueExtractor: PlaceClueExtractor,
    private val searchPlaceCandidates: SearchPlaceCandidatesUseCase,
    private val candidateSelector: PlaceCandidateSelector,
    private val retryBackoffs: List<Duration>,
    private val processingTimeout: Duration,
    private val imageOcrConcurrency: Int = DEFAULT_IMAGE_OCR_CONCURRENCY,
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
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_TEXT_CLUES)
        val textClues = (job.textClues ?: extractClues(job)).filterGroundedTextClues(job)
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_TEXT_RESOLUTION)
        val textResolution = resolveClues(job, textClues, expectedPlaceCount, useEvidenceImageSequence = false)
        logOcrDecision(eventLogger, job, textClues.size, textResolution.places.size, expectedPlaceCount)
        val imageResolution = resolveImageClues(
            job = job,
            textClueCount = textClues.size,
            textResolvedCount = textResolution.places.size,
            expectedPlaceCount = expectedPlaceCount,
        )
        val places = (textResolution.places + imageResolution?.places.orEmpty())
            .distinctBy { it.provider to it.externalPlaceId }
            .distinctLogicalPlaces()
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
        val diagnostics = placeParsingDiagnostics(
            textExpectedPlaceCount = expectedPlaceCount,
            imageExpectedPlaceCount = imageResolution?.expectedPlaceCount,
            extractedPlaceCount = textResolution.clueCount + (imageResolution?.clueCount ?: 0),
            resolvedPlaceCount = places.size,
            unresolvedClues = textResolution.unresolvedClues + imageResolution?.unresolvedClues.orEmpty(),
        )
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_SAVE)
        measure(job, COMPLETE_STAGE) { jobPort.complete(job.postId, places, diagnostics) }
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
                mapOf(
                    "place.outcome" to diagnostics.outcome,
                    "place.expected_count" to diagnostics.expectedPlaceCount,
                    "place.extracted_count" to diagnostics.extractedPlaceCount,
                    "place.resolved_count" to places.size,
                    "place.unresolved_count" to diagnostics.unresolvedClues.size,
                ),
            ),
        )
        return Result.Completed
    }

    @Suppress("LongMethod") // Completeness recovery and progress milestones form one orchestration boundary.
    private fun resolveImageClues(
        job: ClaimedPlaceParsingJob,
        textClueCount: Int,
        textResolvedCount: Int,
        expectedPlaceCount: Int?,
    ): ClueResolution? {
        val images = job.imageUrls.take(MAX_IMAGE_COUNT).mapIndexed { index, imageUrl ->
            ImageTextExtractor.ImageInput(imageIndex = index + 1, imageUrl = imageUrl)
        }
        if (images.isEmpty() || !requiresImageAnalysis(textClueCount, textResolvedCount, expectedPlaceCount)) {
            return null
        }
        logger.info {
            "Place parsing image fallback started: postId=${job.postId}, attempt=${job.attempt}, " +
                "imageCount=${images.size}, textClueCount=$textClueCount, textResolvedCount=$textResolvedCount, " +
                "expectedPlaceCount=$expectedPlaceCount"
        }
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_IMAGE_OCR)
        val transcripts = job.imageTranscripts ?: measure(job, IMAGE_TRANSCRIPT_STAGE) {
            extractTranscriptsWithLatestUrlFallback(
                job.postId,
                images,
                imageTextExtractor,
                imageUrlPort,
                imageOcrConcurrency,
            )
        }.also { extracted ->
            logger.info {
                "Image transcripts received: postId=${job.postId}, attempt=${job.attempt}, " +
                    "imageCount=${images.size}, transcriptCount=${extracted.size}"
            }
            jobPort.storeImageTranscripts(job.postId, extracted)
        }
        val effectiveExpectedPlaceCount = effectiveExpectedPlaceCount(expectedPlaceCount, transcripts)
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_IMAGE_CLUES)
        val primaryImageClues = extractClues(job, transcripts)
            .map { clue -> clue.restoreGroundingFromCard(transcripts) }
            .filterGroundedImageClues(images.size, job.postId, job.attempt, recovered = false)
        val recoveredImageClues = ImageClueRecallRecovery(
            retranscribe = { recoveryImages ->
                measure(job, IMAGE_TRANSCRIPT_STAGE) {
                    extractTranscriptsWithLatestUrlFallback(
                        job.postId,
                        recoveryImages,
                        imageTextExtractor,
                        imageUrlPort,
                        imageOcrConcurrency,
                    )
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
                knownPlaceCount = textClueCount,
                expectedPlaceCount = effectiveExpectedPlaceCount,
            ),
        ).filterGroundedImageClues(images.size, job.postId, job.attempt, recovered = true)
        val imageClues = primaryImageClues + recoveredImageClues
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_IMAGE_RESOLUTION)
        return resolveClues(job, imageClues, effectiveExpectedPlaceCount, useEvidenceImageSequence = true)
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

    private fun resolveClues(
        job: ClaimedPlaceParsingJob,
        clues: List<PlaceClue>,
        expectedPlaceCount: Int?,
        useEvidenceImageSequence: Boolean,
    ): ClueResolution {
        var lastFailure: PlaceResolutionException? = null
        val unresolvedClues = mutableListOf<UnresolvedPlaceClue>()
        val places = clues.mapIndexedNotNull { clueSequence, clue ->
            try {
                resolve(job, clue).copy(
                    sourceMediaSequence = clue.sourceMediaSequence(
                        clueSequence = clueSequence,
                        imageCount = job.imageUrls.size,
                        sourcePlaceCount = expectedPlaceCount ?: clues.size,
                        useEvidenceImageSequence = useEvidenceImageSequence,
                    ),
                    postMediaFallbackAllowed = useEvidenceImageSequence &&
                        clue.hasExclusiveGroundedImageEvidence(clues),
                )
            } catch (exception: PlaceResolutionException) {
                lastFailure = exception
                unresolvedClues += UnresolvedPlaceClue(clue, exception.message.orEmpty())
                logger.warn {
                    "Place clue skipped: postId=${job.postId}, placeName=${clue.name}, " +
                        "region=${clue.region}, reason=${exception.message}"
                }
                null
            }
        }
        return ClueResolution(places, lastFailure, clues.size, expectedPlaceCount, unresolvedClues)
    }

    private fun resolve(job: ClaimedPlaceParsingJob, clue: PlaceClue): PlaceCandidate {
        if (clue.name.isBlank() || clue.searchQueries().isEmpty()) {
            failResolution("Invalid place clue")
        }
        val candidates = searchCandidates(job, clue)
        logger.info {
            "Place candidates searched: placeName=${clue.name}, region=${clue.region}, " +
                "addressHint=${clue.addressHint}, queries=${clue.searchQueries()}, candidateCount=${candidates.size}"
        }
        val selectionCandidates = candidates.compatibleWith(clue)
        val matches = strictMatches(clue, selectionCandidates)
        val groundedMatches = selectionCandidates.filter { candidate ->
            clue.isSupportedBy(candidate.place, candidate.matchedQueries)
        }
        val candidateDescriptions = selectionCandidates.descriptions(CANDIDATE_LOG_LIMIT)
        logger.info {
            "Place candidate matching completed: placeName=${clue.name}, region=${clue.region}, " +
                "candidateCount=${candidates.size}, addressMatchCount=${selectionCandidates.size}, " +
                "strictMatchCount=${matches.size}, groundedMatchCount=${groundedMatches.size}, " +
                "candidates=$candidateDescriptions"
        }

        val selection = uniqueCandidate(matches, groundedMatches) ?: run {
            if (selectionCandidates.isEmpty()) {
                failResolution("No place candidate found: ${clue.name}")
            }
            val selected = measure(job, SELECT_STAGE) {
                candidateSelector.select(PlaceCandidateSelector.Request(clue = clue, candidates = selectionCandidates))
            } ?: failResolution(
                "No place candidate selected: ${clue.name}, strictMatchCount=${matches.size}",
            )
            CandidateSelection(selected, "openai")
        }
        val selectedMatchedQueries = selectionCandidates.matchedQueriesFor(selection.place)
        if (!clue.isSupportedBy(selection.place, selectedMatchedQueries)) {
            failResolution("Selected place is not grounded in image evidence: ${clue.name}")
        }
        eventLogger.info(
            job.event(
                "place.candidate.selected",
                SELECT_STAGE,
                SUCCESS_OUTCOME,
                fields = mapOf(
                    "provider.name" to selection.place.provider,
                    "place.external_id" to selection.place.externalPlaceId,
                    "place.selection_method" to selection.method,
                    "place.candidate_count" to candidates.size,
                    "place.strict_match_count" to matches.size,
                    "place.grounded_match_count" to groundedMatches.size,
                ),
            ),
        )
        logger.info {
            "Place resolved: provider=${selection.place.provider}, " +
                "externalPlaceId=${selection.place.externalPlaceId}, " +
                "name=${selection.place.name}, address=${selection.place.address}"
        }
        return selection.place
    }

    private fun searchCandidates(
        job: ClaimedPlaceParsingJob,
        clue: PlaceClue,
    ): List<PlaceCandidateSelector.Candidate> {
        val candidatesById = linkedMapOf<Pair<String, String>, PlaceCandidateSelector.Candidate>()
        clue.searchQueries().asSequence()
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
        const val DEFAULT_IMAGE_OCR_CONCURRENCY = 4
    }

    private class PlaceResolutionException(message: String) : IllegalStateException(message)

    private class TerminalPlaceParsingException(message: String) : IllegalStateException(message)

    private data class ClueResolution(
        val places: List<PlaceCandidate>,
        val failure: PlaceResolutionException?,
        val clueCount: Int,
        val expectedPlaceCount: Int?,
        val unresolvedClues: List<UnresolvedPlaceClue>,
    )
}

internal fun PlaceClue.sourceMediaSequence(
    clueSequence: Int,
    imageCount: Int,
    sourcePlaceCount: Int,
    useEvidenceImageSequence: Boolean,
): Int {
    if (useEvidenceImageSequence) {
        evidence.minOfOrNull(PlaceClueEvidence::imageIndex)?.let { return (it - 1).coerceAtLeast(0) }
    }
    val coverOffset = if (imageCount == sourcePlaceCount + 1) 1 else 0
    return clueSequence + coverOffset
}

private fun uniqueCandidate(
    strictMatches: List<PlaceCandidateSelector.Candidate>,
    groundedMatches: List<PlaceCandidateSelector.Candidate>,
): CandidateSelection? = when {
    strictMatches.size == 1 -> CandidateSelection(strictMatches.single().place, "strict_match")
    groundedMatches.size == 1 -> CandidateSelection(groundedMatches.single().place, "grounded_match")
    else -> null
}

private data class CandidateSelection(val place: PlaceCandidate, val method: String)

private fun logOcrDecision(
    logger: org.slf4j.Logger,
    job: ClaimedPlaceParsingJob,
    textClueCount: Int,
    textResolvedCount: Int,
    expectedPlaceCount: Int?,
) {
    logger.info(
        job.event(
            "place.ocr.decision",
            "ocr",
            "success",
            fields = mapOf(
                "ocr.required" to requiresImageAnalysis(textClueCount, textResolvedCount, expectedPlaceCount),
                "ocr.reason" to ocrReason(
                    textClueCount,
                    textResolvedCount,
                    expectedPlaceCount,
                    job.imageUrls.isEmpty(),
                ),
                "place.text_clue_count" to textClueCount,
                "place.text_resolved_count" to textResolvedCount,
                "place.expected_count" to expectedPlaceCount,
                "content.image_count" to job.imageUrls.size,
            ),
        ),
    )
}

internal fun PlaceClue.isGroundedIn(body: String?, hashtags: List<String>): Boolean {
    val sources = buildList {
        body?.let(::add)
        addAll(hashtags)
    }.map(String::groundingKey)
    return (listOf(name) + queries)
        .asSequence()
        .map(String::groundingKey)
        .filter { it.length >= MIN_GROUNDING_KEY_LENGTH }
        .any { clueText -> sources.any { source -> source.contains(clueText) } }
}

internal fun requiresImageAnalysis(textClueCount: Int, textResolvedCount: Int, expectedPlaceCount: Int?): Boolean =
    textClueCount == 0 || textResolvedCount == 0 || expectedPlaceCount?.let { textClueCount < it } == true

private fun ocrReason(
    textClueCount: Int,
    textResolvedCount: Int,
    expectedPlaceCount: Int?,
    imagesEmpty: Boolean,
): String = when {
    imagesEmpty -> "no_images"
    textClueCount == 0 -> "no_text_place_clue"
    textResolvedCount == 0 -> "no_text_place_resolved"
    expectedPlaceCount != null && textClueCount < expectedPlaceCount -> "expected_place_clue_shortfall"
    else -> "text_place_clues_sufficient"
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
            (normalizedRegion == null || candidate.place.address.normalize().contains(normalizedRegion)) &&
            PlaceAddressMatcher.isCompatible(clue.addressHint, candidate.place.address)
    }
}

internal fun PlaceClue.searchQueries(): List<String> = buildList {
    addressHint?.trim()?.takeIf(String::isNotEmpty)?.let { address ->
        addAll(PlaceAddressMatcher.searchVariants(address))
    }
    add(name)
    region?.trim()?.takeIf(String::isNotEmpty)?.let { placeRegion ->
        name.split(Regex("\\s+"))
            .map(String::trim)
            .filter { it.length >= MIN_SEARCH_ALIAS_LENGTH }
            .forEach { alias -> add("$placeRegion $alias") }
    }
    addAll(queries)
}.map(String::trim).filter(String::isNotEmpty).distinct().take(MAX_PLACE_QUERY_COUNT)

private fun String.normalize(): String = lowercase().filterNot(Char::isWhitespace)

private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)

private const val MIN_GROUNDING_KEY_LENGTH = 2
private const val MIN_SEARCH_ALIAS_LENGTH = 2
private const val MIN_EXPECTED_PLACE_COUNT = 2
private const val MAX_EXPECTED_PLACE_COUNT = 80
private const val MAX_PLACE_QUERY_COUNT = 4
private val EXPECTED_PLACE_COUNT_PATTERN = Regex("(?<!\\d)(\\d{1,2})\\s*(?:곳|선|군데)")
