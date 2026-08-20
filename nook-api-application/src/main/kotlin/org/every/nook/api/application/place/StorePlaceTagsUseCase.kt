package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ProcessingLogEvent
import org.every.nook.api.application.processing.info
import org.every.nook.api.domain.place.PlaceTag
import org.every.nook.api.domain.place.PlaceTagCategory
import org.slf4j.LoggerFactory
import java.text.Normalizer

class StorePlaceTagsUseCase(
    private val sourcePort: PlaceTagSourcePort,
    private val extractor: PlaceTagExtractor,
    private val updatePort: PlaceTagUpdatePort,
) {
    operator fun invoke(event: PlaceTagsRequestedEvent) {
        val source = findSource(event) ?: return
        val preparedPlaces = preparePlaces(event, source)
        val (skippedPlaces, actionablePlaces) = preparedPlaces.partition { it.input.candidateTags.isEmpty() }
        storeSkippedPlaces(event, skippedPlaces)
        if (actionablePlaces.isEmpty()) return

        val results = extractor.extract(PlaceTagExtractor.Request(actionablePlaces.map(PreparedPlace::input)))
            .associateBy(PlaceTagExtractor.Result::placeIndex)
        storeActionablePlaces(event, actionablePlaces, results)
    }

    private fun findSource(event: PlaceTagsRequestedEvent): PlaceTagSource? {
        val source = sourcePort.find(event.postId)
        if (source == null) {
            logger.info(
                logEvent(
                    event,
                    "place.tags.skipped",
                    "load_source",
                    "skipped",
                    null,
                    mapOf("skip.reason" to "source_not_found"),
                ),
            )
        }
        return source
    }

    private fun preparePlaces(event: PlaceTagsRequestedEvent, source: PlaceTagSource): List<PreparedPlace> {
        val relatedPlaceNames = event.places.map { it.candidate.name }
        val isSinglePlace = relatedPlaceNames.distinctBy(String::normalizedForMatch).size == 1
        return event.places.mapIndexed { index, target ->
            val body = PlaceTagContextSelector.selectBody(source.body, target.candidate.name, relatedPlaceNames)
            val hashtags = if (isSinglePlace) source.hashtags else emptyList()
            PreparedPlace(
                target = target,
                input = PlaceTagExtractor.PlaceInput(
                    placeIndex = index,
                    place = target.candidate,
                    body = body,
                    hashtags = hashtags,
                    candidateTags = PlaceTagCandidateMatcher.find(body, hashtags, target.candidate.name),
                ),
            )
        }
    }

    private fun storeSkippedPlaces(event: PlaceTagsRequestedEvent, skippedPlaces: List<PreparedPlace>) {
        skippedPlaces.forEach { prepared ->
            updatePort.replace(event.postId, prepared.target.placeId, emptyList())
            logger.info(
                logEvent(
                    event,
                    "place.tags.skipped",
                    "match_candidates",
                    "skipped",
                    prepared.target.placeId,
                    mapOf("skip.reason" to "no_matching_keyword"),
                ),
            )
        }
    }

    private fun storeActionablePlaces(
        event: PlaceTagsRequestedEvent,
        actionablePlaces: List<PreparedPlace>,
        results: Map<Int, PlaceTagExtractor.Result>,
    ) {
        actionablePlaces.forEach { prepared ->
            val tags = results[prepared.input.placeIndex]
                ?.tags
                .orEmpty()
                .filterAndLimit(prepared.input.candidateTags)
            updatePort.replace(event.postId, prepared.target.placeId, tags)
            logger.info(
                logEvent(
                    event,
                    "place.tags.completed",
                    "complete",
                    "success",
                    prepared.target.placeId,
                    mapOf("place.tag_count" to tags.size),
                ),
            )
        }
    }

    private fun List<InferredPlaceTag>.filterAndLimit(candidateTags: List<PlaceTag>): List<InferredPlaceTag> {
        val allowedTags = candidateTags.toSet()
        val categoryCounts = mutableMapOf<PlaceTagCategory, Int>()
        return asSequence()
            .filter {
                it.tag in allowedTags &&
                    it.tag.selectable &&
                    it.confidence >= MIN_CONFIDENCE &&
                    it.evidenceText.isGroundedEvidence()
            }
            .distinctBy(InferredPlaceTag::tag)
            .sortedByDescending(InferredPlaceTag::confidence)
            .filter { inferred ->
                val category = inferred.tag.category
                val count = categoryCounts.getOrDefault(category, 0)
                if (count >= MAX_TAG_COUNT_PER_CATEGORY) {
                    false
                } else {
                    categoryCounts[category] = count + 1
                    true
                }
            }
            .take(MAX_TAG_COUNT)
            .toList()
    }

    private fun logEvent(
        event: PlaceTagsRequestedEvent,
        action: String,
        stage: String,
        outcome: String,
        placeId: Long?,
        fields: Map<String, Any?>,
    ) = ProcessingLogEvent(
        action = action,
        flow = TAG_FLOW,
        stage = stage,
        outcome = outcome,
        sourcePostId = event.postId,
        fields = fields + mapOf("place.id" to placeId),
    )

    private data class PreparedPlace(
        val target: PlaceTagsRequestedEvent.Place,
        val input: PlaceTagExtractor.PlaceInput,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(StorePlaceTagsUseCase::class.java)
        const val TAG_FLOW = "place-tags"
        const val MAX_TAG_COUNT = 4
        const val MAX_TAG_COUNT_PER_CATEGORY = 2
        const val MIN_CONFIDENCE = 0.65
    }
}

private fun String.isGroundedEvidence(): Boolean {
    val normalized = normalizedForMatch()
    return UNGROUNDED_EVIDENCE_MARKERS.none(normalized::contains)
}

private val UNGROUNDED_EVIDENCE_MARKERS = listOf(
    "언급이없",
    "표현은없",
    "근거가없",
    "근거는없",
    "보이지않",
    "포함되어있지않",
    "선택하지않",
    "해당하지않",
    "암시",
    "시사",
    "연상",
    "해석할수",
    "여지가있",
    "가능성이있",
)

internal object PlaceTagCandidateMatcher {
    fun find(body: String?, hashtags: List<String>, placeName: String): List<PlaceTag> {
        val normalizedText = buildString {
            body?.takeIf(String::isNotBlank)?.let { append(it).append('\n') }
            hashtags.forEach { append(it).append('\n') }
        }.normalizedForMatch().replace(placeName.normalizedForMatch(), "")
        if (normalizedText.isBlank()) return emptyList()
        return PlaceTag.selectableEntries.filter { tag ->
            tag.matchingKeywords.any { keyword -> normalizedText.contains(keyword.normalizedForMatch()) }
        }
    }
}

internal object PlaceTagContextSelector {
    fun selectBody(body: String?, placeName: String, relatedPlaceNames: List<String>): String? {
        val source = body?.trim()?.takeIf(String::isNotBlank) ?: return null
        val distinctNames = relatedPlaceNames.distinctBy(String::normalizedForMatch)
        return if (distinctNames.size <= 1) {
            source
        } else {
            selectMultiPlaceBody(source, placeName, distinctNames)
        }
    }

    private fun selectMultiPlaceBody(source: String, placeName: String, distinctNames: List<String>): String? {
        val lines = source.lines()
        val start = lines.indexOfFirst { line -> line.references(placeName) }
        if (start < 0) return null
        val otherNames = distinctNames.filterNot { it.normalizedForMatch() == placeName.normalizedForMatch() }
        val end = (start + 1 until lines.size).firstOrNull { index ->
            otherNames.any { otherName -> lines[index].references(otherName) }
        } ?: lines.size
        return lines.subList(start, end).joinToString("\n").trim().takeIf(String::isNotBlank)
    }

    private fun String.references(placeName: String): Boolean {
        val normalizedLine = normalizedForMatch()
        val normalizedName = placeName.normalizedForMatch()
        if (normalizedName.isNotBlank() && normalizedLine.contains(normalizedName)) return true
        return placeName.split(Regex("[^가-힣A-Za-z0-9]+"))
            .map(String::normalizedForMatch)
            .filter { it.length >= MIN_DISTINCTIVE_TOKEN_LENGTH && it !in GENERIC_PLACE_TOKENS }
            .any(normalizedLine::contains)
    }

    private const val MIN_DISTINCTIVE_TOKEN_LENGTH = 2
    private val GENERIC_PLACE_TOKENS = setOf("카페", "식당", "음식점", "본점", "지점")
}

private fun String.normalizedForMatch(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)
    .lowercase()
    .replace(Regex("[^가-힣a-z0-9]+"), "")
