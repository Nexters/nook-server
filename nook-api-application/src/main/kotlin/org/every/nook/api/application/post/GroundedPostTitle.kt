package org.every.nook.api.application.post

internal fun groundedPostTitle(body: String?, inferredTitle: String): String =
    body?.let(EXPLICIT_COLLECTION_TITLE_PATTERN::find)?.let { match ->
        val region = match.groups["region"]?.value.orEmpty()
        val category = match.groups["category"]?.value.orEmpty()
        val count = match.groups["count"]?.value.orEmpty()
        "$region $category ${count}곳"
    } ?: body?.collectionHeadline() ?: inferredTitle

private fun String.collectionHeadline(): String? {
    val firstMeaningfulLine = lineSequence()
        .map(String::trim)
        .map { line -> line.dropWhile { character -> !character.isLetterOrDigit() }.trim() }
        .firstOrNull(String::isNotEmpty)
        ?: return null
    val firstSentence = firstMeaningfulLine
        .takeWhile { character -> character !in TITLE_SENTENCE_DELIMITERS }
        .trim()
    return firstSentence.takeIf { headline ->
        headline.length in 1..MAX_TITLE_LENGTH &&
            COLLECTION_CATEGORY_PATTERN.containsMatchIn(headline) &&
            COLLECTION_COUNT_PATTERN.containsMatchIn(headline)
    }
}

private val EXPLICIT_COLLECTION_TITLE_PATTERN = Regex(
    "(?<region>서울|부산|대구|인천|광주|대전|울산|세종|제주(?:도)?|[가-힣]{1,8}(?:시|군|구|동|읍|면|리))" +
        "\\s*(?<category>카페|맛집|식당|음식점|술집|바|빵집|베이커리|숙소)" +
        "[^\\d\\n]{0,12}(?<count>\\d{1,2})\\s*(?:곳|개)",
)

private const val MAX_TITLE_LENGTH = 25
private val TITLE_SENTENCE_DELIMITERS = setOf('.', '!', '?', '。')
private val COLLECTION_CATEGORY_PATTERN = Regex("카페|맛집|식당|음식점|술집|바|빵집|베이커리|숙소")
private val COLLECTION_COUNT_PATTERN = Regex("(?<!\\d)\\d{1,2}\\s*(?:곳|개|선|군데)")
