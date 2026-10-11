package org.every.nook.api.application.place

import org.every.nook.api.application.processing.ParsingRuleOutcome

internal fun PlaceClue.searchQueries(): List<String> = searchPlan().let { it.primary + it.fallback }

internal fun PlaceClue.searchPlan(): PlaceClueSearchPlan {
    val allQueries = orderedSearchQueries()
    val primaryIsKorean = name.hasHangul()
    return PlaceClueSearchPlan(
        primary = allQueries.filter { it.hasHangul() == primaryIsKorean }.take(PRIMARY_QUERY_COUNT),
        fallback = allQueries.filter { it.hasHangul() != primaryIsKorean }.take(FALLBACK_QUERY_COUNT),
    )
}

internal fun PlaceClue.resolveWithLanguageFallback(resolve: (List<String>) -> PlaceCandidate): PlaceCandidate {
    val plan = searchPlan()
    return try {
        resolve(plan.primary)
    } catch (exception: PlaceResolutionException) {
        if (plan.fallback.isEmpty()) throw exception
        resolve(plan.fallback)
    }
}

internal fun Collection<PlaceCandidateSelector.Candidate>.hasValidatedStrictMatch(clue: PlaceClue): Boolean {
    val match = strictMatches(clue, this).singleOrNull() ?: return false
    return CandidateResolutionPolicy().evaluateSelectionValidation(
        clue,
        CandidateSelection(match.place, "strict_match"),
        toList(),
        null,
    ).outcome == ParsingRuleOutcome.PASSED
}

private fun PlaceClue.orderedSearchQueries(): List<String> = buildList {
    val normalizedAddress = validatedNormalizedAddress()
    normalizedAddress?.let { add(it) }
    addressHint?.trim()?.takeIf(String::isNotEmpty)?.let { address ->
        addAll(PlaceAddressMatcher.searchVariants(address))
    }
    add(name)
    if (normalizedAddress != null) addAll(queries)
    region?.trim()?.takeIf(String::isNotEmpty)?.let { placeRegion ->
        name.split(Regex("\\s+"))
            .map(String::trim)
            .filter { it.length >= MIN_SEARCH_ALIAS_LENGTH }
            .forEach { alias -> add("$placeRegion $alias") }
    }
    addAll(queries)
}.map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::searchQueryIdentity)

private fun String.hasHangul(): Boolean = any { it in '가'..'힣' }

private fun String.searchQueryIdentity(): String = lowercase()
    .split(Regex("[^가-힣a-z0-9]+"))
    .filter(String::isNotEmpty)
    .sorted()
    .joinToString("|")

internal data class PlaceClueSearchPlan(val primary: List<String>, val fallback: List<String>)

private const val MIN_SEARCH_ALIAS_LENGTH = 2
private const val PRIMARY_QUERY_COUNT = 4
private const val FALLBACK_QUERY_COUNT = 3
