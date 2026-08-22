package org.every.nook.api.application.place

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
}.map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::searchQueryIdentity)
    .take(MAX_PLACE_QUERY_COUNT)

private fun String.searchQueryIdentity(): String = lowercase()
    .split(Regex("[^가-힣a-z0-9]+"))
    .filter(String::isNotEmpty)
    .sorted()
    .joinToString("|")

private const val MIN_SEARCH_ALIAS_LENGTH = 2
private const val MAX_PLACE_QUERY_COUNT = 4
