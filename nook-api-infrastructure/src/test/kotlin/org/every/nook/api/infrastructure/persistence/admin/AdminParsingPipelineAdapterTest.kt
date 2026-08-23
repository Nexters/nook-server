package org.every.nook.api.infrastructure.persistence.admin

import org.every.nook.api.application.place.HangulOcrRuleSpec
import org.every.nook.api.application.place.PlaceParsingDiagnostics
import org.every.nook.api.application.place.UnresolvedPlaceClue
import org.every.nook.api.application.processing.ParsingProgressStage
import org.every.nook.api.application.processing.ParsingStepId
import org.every.nook.api.application.processing.PlaceParsingPolicyCatalog
import org.every.nook.api.domain.place.PlaceParsingStatus
import org.every.nook.api.domain.post.PostContentParsingStatus
import org.every.nook.api.infrastructure.persistence.config.RuntimeConfigurationEntity
import org.every.nook.api.infrastructure.persistence.config.RuntimeConfigurationJpaRepository
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobEntity
import org.every.nook.api.infrastructure.persistence.place.PlaceParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobEntity
import org.every.nook.api.infrastructure.persistence.post.PostContentParsingJobJpaRepository
import org.every.nook.api.infrastructure.persistence.post.PostEntity
import org.every.nook.api.infrastructure.persistence.post.PostJpaRepository
import org.every.nook.api.infrastructure.persistence.processing.ProcessingTraceEntity
import org.every.nook.api.infrastructure.persistence.processing.ProcessingTraceJpaRepository
import org.every.nook.api.infrastructure.place.GooglePlacePhotoRuleSpec
import org.every.nook.api.infrastructure.place.PlaceThumbnailProperties
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminParsingPipelineAdapterTest {
    private val configurationRepository = mock(RuntimeConfigurationJpaRepository::class.java)
    private val postRepository = mock(PostJpaRepository::class.java)
    private val contentJobRepository = mock(PostContentParsingJobJpaRepository::class.java)
    private val placeJobRepository = mock(PlaceParsingJobJpaRepository::class.java)
    private val traceRepository = mock(ProcessingTraceJpaRepository::class.java)
    private val now = Instant.parse("2026-08-22T00:00:00Z")

    @Test
    fun `returns code backed rules and effective runtime chains`() {
        configure("instagram.scraping.provider-mode", "APIFY_ONLY")
        configure("ocr.image-text.provider-chain", "COREPIN,CLOVA,OPENAI")
        configure("place.thumbnail.provider-chain", "APIFY_GOOGLE,POST_MEDIA")

        val result = adapter().get(null)

        assertEquals("APIFY_ONLY", result.configurations.first().effectiveValue)
        assertEquals("COREPIN → CLOVA → OPENAI", result.configurations[1].effectiveValue)
        assertEquals("APIFY_GOOGLE → POST_MEDIA", result.configurations[2].effectiveValue)
        val imageOcrRules = result.nodes.first { it.id == "image-ocr" }.sections.flatMap { it.rules }
        assertTrue(imageOcrRules.any { it.value == HangulOcrRuleSpec.MAX_EDIT_DISTANCE.toString() })
        assertTrue(result.nodes.all { it.decisions.isNotEmpty() })
        val hangulDecision = result.nodes.first { it.id == "image-ocr" }.decisions
            .first { it.title == "한글 근접 판정" }
        assertTrue(
            requireNotNull(hangulDecision.expression)
                .contains("distance <= ${HangulOcrRuleSpec.MAX_EDIT_DISTANCE}"),
        )
        assertEquals("HangulOcrMatcher", hangulDecision.source)
        val photoRules = result.nodes.first { it.id == "thumbnail" }.sections.flatMap { it.rules }
        assertTrue(photoRules.any { it.value == GooglePlacePhotoRuleSpec.MIN_MATCH_SCORE.toString() })

        listOf(
            "text-clues",
            "source-coverage",
            "text-resolution",
            "image-decision",
            "image-resolution",
            "title-finalization",
        )
            .forEach { stepId ->
                val catalog = PlaceParsingPolicyCatalog.catalog
                val expectedRuleIds = requireNotNull(catalog.step(ParsingStepId(stepId))).ruleIds.map { it.value }
                val exposedRuleIds = result.nodes.first { it.id == stepId }.decisions.map { it.ruleId }
                assertEquals(expectedRuleIds, exposedRuleIds)
            }
    }

    @Test
    fun `invalid thumbnail chain exposes environment fallback warning`() {
        configure("place.thumbnail.provider-chain", "UNKNOWN")

        val configuration = adapter(PlaceThumbnailProperties.Provider.GOOGLE).get(null)
            .configurations.first { it.key == "place.thumbnail.provider-chain" }

        assertEquals("GOOGLE", configuration.effectiveValue)
        assertEquals("ENVIRONMENT_FALLBACK", configuration.source)
        assertTrue(configuration.warnings.isNotEmpty())
    }

    @Test
    fun `post execution includes current milestone progress and attempts`() {
        val post = PostEntity("INSTAGRAM", "external", "https://instagram.com/p/external", title = "성수 카페")
        val contentJob = PostContentParsingJobEntity(postId = 7, status = PostContentParsingStatus.COMPLETED)
        val placeJob = PlaceParsingJobEntity(
            postId = 7,
            status = PlaceParsingStatus.PROCESSING,
            attemptCount = 2,
        ).apply {
            advanceProgress(ParsingProgressStage.PLACE_IMAGE_OCR, now.minusSeconds(5))
        }
        `when`(postRepository.findById(7)).thenReturn(Optional.of(post))
        `when`(contentJobRepository.findByPostId(7)).thenReturn(contentJob)
        `when`(placeJobRepository.findByPostId(7)).thenReturn(placeJob)
        `when`(traceRepository.findAllByPostIdOrderByCreatedAtAscIdAsc(7)).thenReturn(emptyList())

        val execution = requireNotNull(adapter().get(7).execution)

        assertEquals("PLACE_IMAGE_OCR", execution.place?.stage)
        assertEquals(2, execution.place?.attemptCount)
        assertTrue(requireNotNull(execution.place).progressPercent in 74..82)
        assertEquals(100, execution.content.progressPercent)
    }

    @Test
    fun `post execution exposes partial place diagnostics`() {
        val post = PostEntity("INSTAGRAM", "external", "https://instagram.com/p/external", title = "성수 편집숍")
        val placeJob = PlaceParsingJobEntity(
            postId = 520,
            status = PlaceParsingStatus.COMPLETED,
            parsingOutcome = PlaceParsingDiagnostics.Outcome.PARTIAL,
            expectedPlaceCount = 7,
            extractedPlaceCount = 7,
            resolvedPlaceCount = 4,
            unresolvedPlaceClues = """[{"clue":{"name":"Tune","region":"성수동",""" +
                """"queries":["성수동 Tune"],"evidence":[],"addressHint":null},""" +
                """"reason":"No place candidate selected: Tune"}]""",
        )
        `when`(postRepository.findById(520)).thenReturn(Optional.of(post))
        `when`(placeJobRepository.findByPostId(520)).thenReturn(placeJob)
        `when`(traceRepository.findAllByPostIdOrderByCreatedAtAscIdAsc(520)).thenReturn(emptyList())

        val place = requireNotNull(adapter().get(520).execution?.place)

        assertEquals("PARTIAL", place.outcome)
        assertEquals(4, place.resolvedPlaceCount)
        assertEquals("Tune", place.unresolvedPlaceClues.single().clue.name)
        assertEquals(UnresolvedPlaceClue.Type.RESOLUTION_FAILED, place.unresolvedPlaceClues.single().type)
    }

    @Test
    fun `post execution includes ordered structured traces`() {
        val post = PostEntity("INSTAGRAM", "external", "https://instagram.com/p/external", title = "용산 카페")
        val trace = mock(ProcessingTraceEntity::class.java)
        `when`(postRepository.findById(475)).thenReturn(Optional.of(post))
        `when`(traceRepository.findAllByPostIdOrderByCreatedAtAscIdAsc(475)).thenReturn(listOf(trace))
        `when`(trace.id).thenReturn(9)
        `when`(trace.flow).thenReturn("place")
        `when`(trace.stage).thenReturn("match")
        `when`(trace.action).thenReturn("place.candidates.matched")
        `when`(trace.outcome).thenReturn("failure")
        `when`(trace.attempt).thenReturn(1)
        `when`(trace.durationMs).thenReturn(12)
        `when`(trace.details).thenReturn("{\"candidateCount\":\"3\",\"addressCompatibleCount\":\"0\"}")
        `when`(trace.createdAt).thenReturn(now)

        val execution = requireNotNull(adapter().get(475).execution)

        assertEquals("3", execution.traces.single().details["candidateCount"])
        assertEquals("failure", execution.traces.single().outcome)
    }

    private fun configure(key: String, value: String) {
        `when`(configurationRepository.findByConfigurationKey(key)).thenReturn(
            RuntimeConfigurationEntity(key, value),
        )
    }

    private fun adapter(
        thumbnailProvider: PlaceThumbnailProperties.Provider = PlaceThumbnailProperties.Provider.POST_MEDIA,
    ) = AdminParsingPipelineAdapter(
        configurationRepository,
        postRepository,
        contentJobRepository,
        placeJobRepository,
        traceRepository,
        PlaceThumbnailProperties(thumbnailProvider),
        jacksonObjectMapper(),
        Clock.fixed(now, ZoneOffset.UTC),
    )
}
