package org.every.nook.api.application.place

import mu.KotlinLogging
import org.every.nook.api.application.post.FinalizePostTitle
import org.every.nook.api.application.post.PostTitleSelector
import org.every.nook.api.application.processing.NoOpProcessingMetrics
import org.every.nook.api.application.processing.NoOpProcessingTracePort
import org.every.nook.api.application.processing.ParsingProgressStage
import org.every.nook.api.application.processing.ParsingRuleEvaluation
import org.every.nook.api.application.processing.ProcessingMetrics
import org.every.nook.api.application.processing.ProcessingTraceEvent
import org.every.nook.api.application.processing.ProcessingTracePort
import org.every.nook.api.application.processing.error
import org.every.nook.api.application.processing.info
import org.every.nook.api.application.processing.measure
import org.every.nook.api.application.processing.traceDetails
import org.every.nook.api.application.processing.warn
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

// Trace recording stays at orchestration decisions so operators see the exact path taken by a job.
@Suppress("LongMethod", "TooManyFunctions")
class ProcessPlaceParsingJobUseCase(
    private val jobPort: PlaceParsingJobPort,
    private val imageTextExtractor: ImageTextExtractor,
    private val imageUrlPort: PlaceImageUrlPort = PlaceImageUrlPort { emptyList() },
    private val clueExtractor: PlaceClueExtractor,
    private val searchPlaceCandidates: SearchPlaceCandidatesUseCase,
    private val candidateSelector: PlaceCandidateSelector,
    titleSelector: PostTitleSelector = PostTitleSelector {
        PostTitleSelector.Result(null, PostTitleSelector.Source.NONE, emptyList(), null)
    },
    private val retryBackoffs: List<Duration>,
    private val processingTimeout: Duration,
    private val imageOcrConcurrency: Int = DEFAULT_IMAGE_OCR_CONCURRENCY,
    private val metrics: ProcessingMetrics = NoOpProcessingMetrics,
    private val tracePort: ProcessingTracePort = NoOpProcessingTracePort,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val finalizePostTitle = FinalizePostTitle(titleSelector)

    operator fun invoke(postId: Long): Result {
        val job = jobPort.claim(postId, processingTimeout) ?: return Result.Skipped
        val startedAt = clock.instant()
        eventLogger.info(job.event("place.job.claimed", JOB_STAGE, SUCCESS_OUTCOME))
        recordTrace(job, JOB_STAGE, "place.job.claimed", SUCCESS_OUTCOME)
        logger.info { "Place parsing started: postId=${job.postId}, attempt=${job.attempt}" }

        return runCatching { process(job, startedAt) }.getOrElse { exception ->
            handleFailure(job, exception, startedAt)
        }
    }

    private fun process(job: ClaimedPlaceParsingJob, startedAt: Instant): Result {
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_TEXT_CLUES)
        val extractedTextClues = job.textClues ?: extractClues(job)
        val initialCoverage = SourcePlaceCoveragePolicy().evaluate(
            SourcePlaceCoveragePolicy.Context(job.body, extractedTextClues),
        ).result
        val expectedPlaceCount = maxExpectedPlaceCount(
            expectedPlaceCount(job.body),
            initialCoverage.expectedPlaceCount,
        )
        val textClues = extractedTextClues.filterGroundedTextClues(job) { clue, evaluation ->
            recordRuleTrace(job, TEXT_CLUE_STAGE, evaluation, clue)
        }
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_TEXT_RESOLUTION)
        val textResolution = resolveClues(job, textClues, expectedPlaceCount, useEvidenceImageSequence = false)
        val imageDecision = ImageAnalysisPolicy().evaluate(
            ImageAnalysisPolicy.Context(
                textClueCount = textClues.size,
                textResolvedCount = textResolution.places.size,
                expectedPlaceCount = expectedPlaceCount,
                imageCount = job.imageUrls.take(MAX_IMAGE_COUNT).size,
            ),
        ).also { evaluation ->
            evaluation.ruleEvaluations.forEach { recordRuleTrace(job, OCR_STAGE, it) }
        }.result
        logOcrDecision(eventLogger, job, textClues.size, textResolution.places.size, expectedPlaceCount, imageDecision)
        val imageResolution = resolveImageClues(
            job = job,
            textClueCount = textClues.size,
            textResolvedCount = textResolution.places.size,
            expectedPlaceCount = expectedPlaceCount,
            imageAnalysisRequired = imageDecision.required,
        )
        val places = (textResolution.places + imageResolution?.places.orEmpty())
            .distinctBy { it.provider to it.externalPlaceId }
            .distinctLogicalPlaces()
        if (places.isEmpty()) {
            failWithFinalizedTitle(job, textResolution, imageResolution, expectedPlaceCount)
        }
        val finalCoverage = SourcePlaceCoveragePolicy().evaluate(
            SourcePlaceCoveragePolicy.Context(job.body, textResolution.clues + imageResolution?.clues.orEmpty()),
        ).also { evaluation ->
            evaluation.ruleEvaluations.forEach { recordRuleTrace(job, COVERAGE_STAGE, it) }
        }.result
        val diagnostics = placeParsingDiagnostics(
            textExpectedPlaceCount = expectedPlaceCount,
            imageExpectedPlaceCount = imageResolution?.expectedPlaceCount,
            extractedPlaceCount = textResolution.clueCount + (imageResolution?.clueCount ?: 0),
            resolvedPlaceCount = places.size,
            unresolvedClues = textResolution.unresolvedClues +
                imageResolution?.unresolvedClues.orEmpty() +
                finalCoverage.unresolvedClues(),
        )
        jobPort.updateProgress(job.postId, ParsingProgressStage.TITLE_FINALIZATION)
        val titleTranscripts = imageResolution?.imageTranscripts.orEmpty()
        val title = measure(job, TITLE_STAGE) {
            finalizePostTitle(job, places, expectedPlaceCount, titleTranscripts) {
                recordRuleTrace(job, TITLE_STAGE, it)
            }
        }
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_SAVE)
        measure(job, COMPLETE_STAGE) { jobPort.complete(job.postId, title, places, diagnostics) }
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
        recordTrace(
            job,
            JOB_STAGE,
            "place.job.completed",
            SUCCESS_OUTCOME,
            duration,
            mapOf(
                "expectedPlaceCount" to diagnostics.expectedPlaceCount?.toString().orEmpty(),
                "extractedPlaceCount" to diagnostics.extractedPlaceCount.toString(),
                "resolvedPlaceCount" to places.size.toString(),
                "unresolvedPlaceCount" to diagnostics.unresolvedClues.size.toString(),
            ).filterValues(String::isNotEmpty),
        )
        return Result.Completed
    }

    private fun failWithFinalizedTitle(
        job: ClaimedPlaceParsingJob,
        textResolution: ClueResolution,
        imageResolution: ClueResolution?,
        expectedPlaceCount: Int?,
    ): Nothing {
        val failure = textResolution.failure ?: imageResolution?.failure
        jobPort.updateProgress(job.postId, ParsingProgressStage.TITLE_FINALIZATION)
        val title = measure(job, TITLE_STAGE) {
            finalizePostTitle(
                job = job,
                places = emptyList(),
                declaredPlaceCount = expectedPlaceCount,
                latestImageTranscripts = imageResolution?.imageTranscripts.orEmpty(),
                onEvaluation = { recordRuleTrace(job, TITLE_STAGE, it) },
            )
        }
        val reason = failure?.message ?: if (imageResolution == null) {
            NO_PLACE_RESOLVED_REASON
        } else {
            NO_PLACE_RESOLVED_AFTER_IMAGE_REASON
        }
        throw TerminalPlaceParsingException(reason, title)
    }

    @Suppress("LongMethod") // Completeness recovery and progress milestones form one orchestration boundary.
    private fun resolveImageClues(
        job: ClaimedPlaceParsingJob,
        textClueCount: Int,
        textResolvedCount: Int,
        expectedPlaceCount: Int?,
        imageAnalysisRequired: Boolean,
    ): ClueResolution? {
        val images = job.imageUrls.take(MAX_IMAGE_COUNT).mapIndexed { index, imageUrl ->
            ImageTextExtractor.ImageInput(imageIndex = index + 1, imageUrl = imageUrl)
        }
        if (!imageAnalysisRequired) {
            return null
        }
        logger.info {
            "Place parsing image fallback started: postId=${job.postId}, attempt=${job.attempt}, " +
                "imageCount=${images.size}, textClueCount=$textClueCount, textResolvedCount=$textResolvedCount, " +
                "expectedPlaceCount=$expectedPlaceCount"
        }
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_IMAGE_OCR)
        val cachedTranscripts = job.imageTranscripts.orEmpty()
            .filter { transcript -> images.any { it.imageIndex == transcript.imageIndex } }
        val cachedByIndex = cachedTranscripts.associateBy(ImageTranscript::imageIndex)
        val missingImages = images.filter { image ->
            cachedByIndex[image.imageIndex]?.texts?.any(String::isNotBlank) != true
        }
        val transcripts = if (missingImages.isEmpty()) {
            cachedTranscripts.sortedBy(ImageTranscript::imageIndex)
        } else {
            val extracted = measure(job, IMAGE_TRANSCRIPT_STAGE) {
                extractTranscriptsWithLatestUrlFallback(
                    job.postId,
                    missingImages,
                    imageTextExtractor,
                    imageUrlPort,
                    imageOcrConcurrency,
                )
            }
            val extractedByIndex = extracted.associateBy(ImageTranscript::imageIndex)
            images.map { image ->
                extractedByIndex[image.imageIndex] ?: cachedByIndex[image.imageIndex]
                    ?: ImageTranscript(image.imageIndex, emptyList())
            }
        }.also { merged ->
            logger.info {
                "Image transcripts received: postId=${job.postId}, attempt=${job.attempt}, " +
                    "imageCount=${images.size}, cachedCount=${cachedTranscripts.size}, " +
                    "requestedCount=${missingImages.size}, transcriptCount=${merged.size}"
            }
            if (missingImages.isNotEmpty()) {
                jobPort.storeImageTranscripts(job.postId, merged)
            }
        }
        val effectiveExpectedPlaceCount = effectiveExpectedPlaceCount(expectedPlaceCount, transcripts)
        jobPort.updateProgress(job.postId, ParsingProgressStage.PLACE_IMAGE_CLUES)
        val primaryImageClues = extractClues(job, transcripts)
            .map { clue -> clue.restoreGroundingFromCard(transcripts) }
            .reconcileWithNumberedPlaceCards(transcripts)
            .reconcileWithSourceProfileHints(job.sourceProfileHints)
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
            extractClues = { recoveryTranscripts ->
                extractClues(job, recoveryTranscripts)
                    .map { clue -> clue.restoreGroundingFromCard(recoveryTranscripts) }
                    .reconcileWithNumberedPlaceCards(recoveryTranscripts)
                    .reconcileWithSourceProfileHints(job.sourceProfileHints)
            },
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
            .copy(imageTranscripts = transcripts)
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
                sourceProfileHints = job.sourceProfileHints,
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
                recordTrace(
                    job,
                    RESOLUTION_STAGE,
                    "place.clue.rejected",
                    FAILURE_OUTCOME,
                    details = clue.traceDetails() + ("reason" to exception.message.orEmpty()),
                )
                null
            }
        }
        return ClueResolution(places, lastFailure, clues.size, expectedPlaceCount, unresolvedClues, clues)
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
        val candidatePolicy = CandidateResolutionPolicy()
        val automaticEvaluation = candidatePolicy.evaluate(CandidateResolutionPolicy.Context(clue, candidates))
            .also { evaluation ->
                evaluation.ruleEvaluations.forEach { recordRuleTrace(job, MATCH_STAGE, it, clue) }
            }
        val automaticResult = automaticEvaluation.result
        val selectionCandidates = automaticResult.compatibleCandidates
        val matches = automaticResult.strictMatches
        val groundedMatches = automaticResult.groundedMatches
        val candidateDescriptions = selectionCandidates.descriptions(CANDIDATE_LOG_LIMIT)
        logger.info {
            "Place candidate matching completed: placeName=${clue.name}, region=${clue.region}, " +
                "candidateCount=${candidates.size}, addressMatchCount=${selectionCandidates.size}, " +
                "strictMatchCount=${matches.size}, groundedMatchCount=${groundedMatches.size}, " +
                "candidates=$candidateDescriptions"
        }
        recordTrace(
            job,
            MATCH_STAGE,
            "place.candidates.matched",
            if (selectionCandidates.isEmpty()) FAILURE_OUTCOME else SUCCESS_OUTCOME,
            details = clue.traceDetails() + mapOf(
                "candidateCount" to candidates.size.toString(),
                "addressCompatibleCount" to selectionCandidates.size.toString(),
                "strictMatchCount" to matches.size.toString(),
                "groundedMatchCount" to groundedMatches.size.toString(),
                "candidates" to candidates.descriptions(CANDIDATE_TRACE_LIMIT).joinToString("\n"),
            ),
        )

        val selection = automaticResult.selection
            ?: run {
                if (selectionCandidates.isEmpty()) {
                    failResolution("No place candidate found: ${clue.name}")
                }
                val selected = measure(job, SELECT_STAGE) {
                    candidateSelector.select(
                        PlaceCandidateSelector.Request(clue = clue, candidates = selectionCandidates),
                    )
                }
                candidatePolicy.evaluateModelSelection(selected).also { recordRuleTrace(job, SELECT_STAGE, it, clue) }
                selected ?: failResolution(
                    "No place candidate selected: ${clue.name}, strictMatchCount=${matches.size}",
                )
                CandidateSelection(selected, "openai")
            }
        val validation = candidatePolicy.evaluateSelectionValidation(
            clue,
            selection,
            selectionCandidates,
            automaticResult.explicitNameSearchMatch,
        ).also { recordRuleTrace(job, SELECT_STAGE, it, clue) }
        if (validation.outcome != org.every.nook.api.application.processing.ParsingRuleOutcome.PASSED) {
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
        recordTrace(
            job,
            SELECT_STAGE,
            "place.candidate.selected",
            SUCCESS_OUTCOME,
            details = mapOf(
                "name" to selection.place.name,
                "address" to selection.place.address,
                "provider" to selection.place.provider,
                "method" to selection.method,
            ),
        )
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
                val found = measure(job, SEARCH_STAGE) {
                    searchPlaceCandidates(SearchPlaceCandidatesUseCase.Command(queries = listOf(query)))
                }
                recordTrace(
                    job,
                    SEARCH_STAGE,
                    "place.search.result",
                    SUCCESS_OUTCOME,
                    details = mapOf(
                        "query" to query,
                        "candidateCount" to found.size.toString(),
                        "candidates" to found.take(CANDIDATE_TRACE_LIMIT)
                            .joinToString("\n") { "${it.provider}|${it.name}|${it.address}" },
                    ),
                )
                found.forEachIndexed { rank, candidate ->
                    val key = candidate.provider to candidate.externalPlaceId
                    val existing = candidatesById[key]
                    candidatesById[key] = PlaceCandidateSelector.Candidate(
                        place = candidate,
                        matchedQueries = (existing?.matchedQueries.orEmpty() + query).distinct(),
                        matchedQueryRanks = existing?.matchedQueryRanks.orEmpty() + (query to rank),
                        supportingProviders = existing?.supportingProviders.orEmpty() + candidate.provider,
                    )
                }
                if (strictMatches(clue, candidatesById.values).size == 1) {
                    return candidatesById.values.toList()
                }
            }
        return candidatesById.values.toList()
    }

    private fun <T> measure(job: ClaimedPlaceParsingJob, stage: String, action: () -> T): T {
        val startedAt = clock.instant()
        return runCatching {
            metrics.measure(PLACE_FLOW, stage, job.postId, job.attempt, clock, action)
        }.onSuccess {
            recordTrace(job, stage, "place.stage.completed", SUCCESS_OUTCOME, elapsedMillis(startedAt))
        }.onFailure { exception ->
            recordTrace(
                job,
                stage,
                "place.stage.failed",
                FAILURE_OUTCOME,
                elapsedMillis(startedAt),
                mapOf("reason" to exception.message.orEmpty().take(FAILURE_REASON_TRACE_LIMIT)),
            )
        }.getOrThrow()
    }

    private fun failResolution(message: String): Nothing = throw PlaceResolutionException(message)

    private fun handleFailure(job: ClaimedPlaceParsingJob, exception: Throwable, startedAt: Instant): Result {
        val reason = placeFailureReason(exception)
        val duration = Duration.between(startedAt, clock.instant()).toMillis()
        if (exception is TerminalPlaceParsingException) {
            jobPort.fail(job.postId, exception.title, reason)
            eventLogger.warn(
                job.event("place.job.failed", JOB_STAGE, FAILURE_OUTCOME, duration, failureFields(exception, reason)),
                exception,
            )
            logger.warn {
                "Place parsing failed without retry: postId=${job.postId}, attempt=${job.attempt}, " +
                    "durationMs=$duration, reason=$reason"
            }
            recordTrace(job, JOB_STAGE, "place.job.failed", FAILURE_OUTCOME, duration, mapOf("reason" to reason))
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
            recordTrace(
                job,
                JOB_STAGE,
                "place.job.retry_scheduled",
                FAILURE_OUTCOME,
                duration,
                mapOf("reason" to reason, "nextAttemptAt" to nextAttemptAt.toString()),
            )
            return Result.Retry(nextAttemptAt)
        }

        val title = finalizePostTitle(
            job = job,
            places = emptyList(),
            declaredPlaceCount = expectedPlaceCount(job.body),
            latestImageTranscripts = emptyList(),
            onEvaluation = { recordRuleTrace(job, TITLE_STAGE, it) },
        )
        jobPort.fail(job.postId, title, reason)
        eventLogger.error(
            job.event("place.job.failed", JOB_STAGE, FAILURE_OUTCOME, duration, failureFields(exception, reason)),
            exception,
        )
        logger.error(exception) {
            "Place parsing failed permanently: postId=${job.postId}, attempt=${job.attempt}, " +
                "durationMs=$duration, reason=$reason"
        }
        recordTrace(job, JOB_STAGE, "place.job.failed", FAILURE_OUTCOME, duration, mapOf("reason" to reason))
        return Result.Failed
    }

    private fun elapsedMillis(startedAt: Instant): Long = Duration.between(startedAt, clock.instant()).toMillis()

    private fun PlaceClue.traceDetails(): Map<String, String> = mapOf(
        "placeName" to name,
        "region" to region.orEmpty(),
        "addressHint" to addressHint.orEmpty(),
        "queries" to searchQueries().joinToString("\n"),
    ).filterValues(String::isNotEmpty)

    private fun recordTrace(
        job: ClaimedPlaceParsingJob,
        stage: String,
        action: String,
        outcome: String,
        durationMs: Long? = null,
        details: Map<String, String> = emptyMap(),
    ) {
        runCatching {
            tracePort.record(
                ProcessingTraceEvent(
                    job.postId,
                    PLACE_FLOW,
                    stage,
                    action,
                    outcome,
                    job.attempt,
                    durationMs,
                    details,
                ),
            )
        }.onFailure { exception ->
            logger.warn(exception) { "Failed to store place parsing trace: postId=${job.postId}" }
        }
    }

    private fun recordRuleTrace(
        job: ClaimedPlaceParsingJob,
        stage: String,
        evaluation: ParsingRuleEvaluation,
        clue: PlaceClue? = null,
    ) {
        recordTrace(
            job = job,
            stage = stage,
            action = "place.rule.evaluated",
            outcome = evaluation.outcome.name.lowercase(),
            details = evaluation.traceDetails() + clue?.traceDetails().orEmpty(),
        )
    }

    sealed interface Result {
        data object Completed : Result

        data class Retry(val nextAttemptAt: Instant) : Result

        data object Failed : Result

        data object Skipped : Result
    }

    private companion object {
        val logger = KotlinLogging.logger {}
        val eventLogger = LoggerFactory.getLogger(ProcessPlaceParsingJobUseCase::class.java)

        const val MAX_PLACE_COUNT = PlaceParsingRuleSpec.MAX_PLACE_COUNT
        const val MAX_QUERY_COUNT = PlaceParsingRuleSpec.MAX_QUERY_COUNT
        const val MAX_IMAGE_COUNT = PlaceParsingRuleSpec.MAX_IMAGE_COUNT
        const val CANDIDATE_LOG_LIMIT = 5
        const val CANDIDATE_TRACE_LIMIT = 10
        const val FAILURE_REASON_TRACE_LIMIT = 500
        const val NO_PLACE_RESOLVED_REASON = "No place could be resolved from text"
        const val NO_PLACE_RESOLVED_AFTER_IMAGE_REASON = "No place could be resolved after image analysis"
        const val PLACE_FLOW = "place"
        const val TEXT_CLUE_STAGE = "clue-text"
        const val IMAGE_TRANSCRIPT_STAGE = "image-transcript"
        const val IMAGE_CLUE_STAGE = "clue-image"
        const val SEARCH_STAGE = "search"
        const val SELECT_STAGE = "select"
        const val MATCH_STAGE = "match"
        const val RESOLUTION_STAGE = "resolution"
        const val COMPLETE_STAGE = "complete"
        const val TITLE_STAGE = "title-finalization"
        const val JOB_STAGE = "job"
        const val OCR_STAGE = "ocr"
        const val COVERAGE_STAGE = "source-coverage"
        const val SUCCESS_OUTCOME = "success"
        const val FAILURE_OUTCOME = "failure"
        const val DEFAULT_IMAGE_OCR_CONCURRENCY = 4
    }

    private class PlaceResolutionException(message: String) : IllegalStateException(message)

    private class TerminalPlaceParsingException(message: String, val title: String) : IllegalStateException(message)

    private data class ClueResolution(
        val places: List<PlaceCandidate>,
        val failure: PlaceResolutionException?,
        val clueCount: Int,
        val expectedPlaceCount: Int?,
        val unresolvedClues: List<UnresolvedPlaceClue>,
        val clues: List<PlaceClue>,
        val imageTranscripts: List<ImageTranscript> = emptyList(),
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

private fun logOcrDecision(
    logger: org.slf4j.Logger,
    job: ClaimedPlaceParsingJob,
    textClueCount: Int,
    textResolvedCount: Int,
    expectedPlaceCount: Int?,
    decision: ImageAnalysisPolicy.Decision,
) {
    logger.info(
        job.event(
            "place.ocr.decision",
            "ocr",
            "success",
            fields = mapOf(
                "ocr.required" to decision.required,
                "ocr.reason" to decision.reason,
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
        .filter { it.length >= TextClueGroundingPolicy.MIN_GROUNDING_KEY_LENGTH }
        .any { clueText -> sources.any { source -> source.contains(clueText) } }
}

internal fun requiresImageAnalysis(textClueCount: Int, textResolvedCount: Int, expectedPlaceCount: Int?): Boolean =
    ImageAnalysisPolicy().evaluate(
        ImageAnalysisPolicy.Context(textClueCount, textResolvedCount, expectedPlaceCount, imageCount = 1),
    ).result.required

private fun expectedPlaceCount(body: String?): Int? = body?.let { content ->
    EXPECTED_PLACE_COUNT_PATTERN.findAll(content)
        .mapNotNull { match -> match.groupValues[1].toIntOrNull() }
        .filter { count ->
            count in PlaceParsingRuleSpec.MIN_EXPECTED_PLACE_COUNT..PlaceParsingRuleSpec.MAX_EXPECTED_PLACE_COUNT
        }
        .maxOrNull()
}

private fun maxExpectedPlaceCount(vararg counts: Int?): Int? = counts.filterNotNull().maxOrNull()

private fun String.groundingKey(): String = lowercase().filter(Char::isLetterOrDigit)

private val EXPECTED_PLACE_COUNT_PATTERN = Regex("(?<!\\d)(\\d{1,2})\\s*(?:곳|선|군데)")
