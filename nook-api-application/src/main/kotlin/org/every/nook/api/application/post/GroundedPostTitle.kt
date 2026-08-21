package org.every.nook.api.application.post

import org.every.nook.api.application.place.ImageTranscript
import org.every.nook.api.domain.post.Post

internal fun groundedPostTitle(body: String?, inferredTitle: String?): String? =
    explicitBodyTitle(body) ?: inferredTitle.validPostTitle()

internal fun resolvePostTitle(body: String?, coverTitle: String?, inferredTitle: String?): String? =
    explicitBodyTitle(body) ?: coverTitle.validPostTitle() ?: inferredTitle.validPostTitle()

private fun explicitBodyTitle(body: String?): String? =
    body?.let(EXPLICIT_COLLECTION_TITLE_PATTERN::find)?.let { match ->
        val region = match.groups["region"]?.value.orEmpty()
        val category = match.groups["category"]?.value.orEmpty()
        val count = match.groups["count"]?.value.orEmpty()
        "$region $category ${count}곳"
    } ?: body?.collectionHeadline()

internal fun String?.validPostTitle(): String? = this
    ?.replace(WHITESPACE_PATTERN, " ")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.takeUnless { title -> title.normalizedTitle() in FORBIDDEN_TITLES }
    ?.takeUnless { title -> EXPLANATION_PATTERNS.any(title::contains) }
    ?.take(Post.MAX_TITLE_LENGTH)

internal fun ImageTranscript.validatedCoverTitle(extractor: CoverTitleExtractor): String? {
    val candidates = texts.mapNotNull { it.validPostTitle() }.distinct()
    if (candidates.isEmpty()) return null
    return extractor.extract(CoverTitleExtractor.Request(candidates))
        .validPostTitle()
        ?.takeIf(candidates::contains)
}

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
        headline.length in 1..MAX_COLLECTION_TITLE_LENGTH &&
            COLLECTION_CATEGORY_PATTERN.containsMatchIn(headline) &&
            COLLECTION_COUNT_PATTERN.containsMatchIn(headline)
    }
}

private val EXPLICIT_COLLECTION_TITLE_PATTERN = Regex(
    "(?<region>서울|부산|대구|인천|광주|대전|울산|세종|제주(?:도)?|[가-힣]{1,8}(?:시|군|구|동|읍|면|리))" +
        "\\s*(?<category>카페|맛집|식당|음식점|술집|바|빵집|베이커리|숙소)" +
        "[^\\d\\n]{0,12}(?<count>\\d{1,2})\\s*(?:곳|개)",
)

private const val MAX_COLLECTION_TITLE_LENGTH = 25
private val WHITESPACE_PATTERN = Regex("\\s+")
private fun String.normalizedTitle(): String = lowercase().filter(Char::isLetterOrDigit)
private val FORBIDDEN_TITLES = setOf(
    "null",
    "nullnull",
    "none",
    "na",
    "제목없음",
    "텍스트없음",
    "instagram게시물",
    "방문해보기좋은곳",
    "방문하기좋은곳",
    "게시물",
)
private val EXPLANATION_PATTERNS = listOf(
    "표시된 날짜",
    "회차 표기가",
    "이미지는",
    "이미지에는",
    "사진에는",
    "보이지 않습니다",
    "확인할 수 없습니다",
)
private val TITLE_SENTENCE_DELIMITERS = setOf('.', '!', '?', '。')
private val COLLECTION_CATEGORY_PATTERN = Regex("카페|맛집|식당|음식점|술집|바|빵집|베이커리|숙소")
private val COLLECTION_COUNT_PATTERN = Regex("(?<!\\d)\\d{1,2}\\s*(?:곳|개|선|군데)")
