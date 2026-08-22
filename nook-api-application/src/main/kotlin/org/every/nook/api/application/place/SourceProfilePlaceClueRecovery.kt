package org.every.nook.api.application.place

import org.every.nook.api.application.content.SourceProfileHint

internal fun List<PlaceClue>.reconcileWithSourceProfileHints(hints: List<SourceProfileHint>): List<PlaceClue> {
    if (isEmpty() || hints.isEmpty()) return this
    val profileNames = hints.flatMap(SourceProfileHint::placeNameCandidates).distinctBy(String::placeClueIdentity)
    if (profileNames.isEmpty()) return this

    return map { clue ->
        if (clue.evidence.isEmpty()) clue else clue.correctNameFrom(profileNames)
    }
        .groupByTo(linkedMapOf()) { clue -> clue.name.placeClueIdentity() }
        .values
        .map { clues -> clues.mergePlaceClues() }
}

internal fun List<PlaceClue>.mergePlaceClues(): PlaceClue {
    require(isNotEmpty())
    val primary = first()
    return primary.copy(
        region = firstNotNullOfOrNull(PlaceClue::region),
        queries = flatMap(PlaceClue::queries).distinct(),
        evidence = flatMap(PlaceClue::evidence)
            .distinctBy { evidence -> evidence.imageIndex to evidence.evidenceText },
        addressHint = firstNotNullOfOrNull(PlaceClue::addressHint),
    )
}

private fun PlaceClue.correctNameFrom(profileNames: List<String>): PlaceClue {
    val clueIdentity = name.hangulIdentity().takeIf { it.length >= MIN_PROFILE_NAME_LENGTH } ?: return this
    val correctedName = profileNames.correctedNameFor(clueIdentity) ?: return this
    val nameChanged = correctedName.placeClueIdentity() != name.placeClueIdentity()
    return copy(
        name = correctedName,
        queries = buildList {
            addAll(correctedName.genericPrefixAliases())
            if (nameChanged) {
                add(correctedName)
                region?.trim()?.takeIf(String::isNotEmpty)?.let { placeRegion -> add("$placeRegion $correctedName") }
                addAll(queries.map { query -> query.replace(name, correctedName, ignoreCase = true) })
            } else {
                addAll(queries)
            }
        }.distinct(),
    )
}

private fun String.genericPrefixAliases(): List<String> = GENERIC_PROFILE_NAME_TOKENS.mapNotNull { prefix ->
    removePrefix(prefix)
        .takeIf { alias -> alias != this && alias.length >= MIN_PROFILE_NAME_LENGTH }
}

private fun List<String>.correctedNameFor(clueIdentity: String): String? {
    val exactNames = filter { profileName ->
        profileName.matchingSegments(clueIdentity.length).any { segment -> segment == clueIdentity }
    }
    return exactNames.singleDistinctNameOrNull() ?: filter { profileName ->
        profileName.matchingSegments(clueIdentity.length).any { segment ->
            HangulOcrMatcher.isNearMatch(clueIdentity, segment)
        }
    }.singleDistinctNameOrNull()
}

private fun List<String>.singleDistinctNameOrNull(): String? = distinctBy(String::placeClueIdentity).singleOrNull()

private fun SourceProfileHint.placeNameCandidates(): List<String> {
    val nameHead = displayName.split(PROFILE_DESCRIPTION_SEPARATOR, limit = 2).first().trim()
    return HANGUL_NAME_PATTERN.findAll(nameHead)
        .map(MatchResult::value)
        .filter { candidate ->
            candidate.length >= MIN_PROFILE_NAME_LENGTH && candidate !in GENERIC_PROFILE_NAME_TOKENS
        }
        .toList()
}

private fun String.matchingSegments(length: Int): Sequence<String> {
    val identity = hangulIdentity()
    if (identity.length < length) return emptySequence()
    return (0..identity.length - length).asSequence().map { start -> identity.substring(start, start + length) }
}

private fun String.hangulIdentity(): String = filter(Char::isHangulSyllable)

private fun Char.isHangulSyllable(): Boolean = code in HANGUL_SYLLABLE_START..HANGUL_SYLLABLE_END

private fun String.placeClueIdentity(): String = lowercase().filter(Char::isLetterOrDigit)

private val PROFILE_DESCRIPTION_SEPARATOR = Regex("[|ㅣᅵ]")
private val HANGUL_NAME_PATTERN = Regex("[가-힣]+")
private val GENERIC_PROFILE_NAME_TOKENS = setOf(
    "카페",
    "식당",
    "음식점",
    "맛집",
    "술집",
    "이자카야",
    "베이커리",
    "레스토랑",
)
private const val MIN_PROFILE_NAME_LENGTH = 2
private const val HANGUL_SYLLABLE_START = 0xAC00
private const val HANGUL_SYLLABLE_END = 0xD7A3
