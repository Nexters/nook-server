package org.every.nook.api.application.post

internal fun groundedPostTitle(body: String?, inferredTitle: String): String =
    body?.let(EXPLICIT_COLLECTION_TITLE_PATTERN::find)?.let { match ->
        val region = match.groups["region"]?.value.orEmpty()
        val category = match.groups["category"]?.value.orEmpty()
        val count = match.groups["count"]?.value.orEmpty()
        "$region $category ${count}곳"
    } ?: inferredTitle

private val EXPLICIT_COLLECTION_TITLE_PATTERN = Regex(
    "(?<region>서울|부산|대구|인천|광주|대전|울산|세종|제주(?:도)?|[가-힣]{1,8}(?:시|군|구|동|읍|면|리))" +
        "\\s*(?<category>카페|맛집|식당|음식점|술집|바|빵집|베이커리|숙소)" +
        "[^\\d\\n]{0,12}(?<count>\\d{1,2})\\s*(?:곳|개)",
)
