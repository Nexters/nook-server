package org.every.nook.api.application.post

fun interface PostTitleSelector {
    fun select(request: Request): Result

    data class Request(
        val body: String?,
        val hashtags: List<String>,
        val sourceLocationTag: String?,
        val coverTexts: List<String>,
        val declaredPlaceCount: Int?,
        val places: List<Place>,
    )

    data class Place(val name: String, val address: String, val city: String?, val category: String?)

    data class Result(
        val title: String?,
        val source: Source,
        val evidence: List<String>,
        val rejectedCoverReason: String?,
    )

    enum class Source {
        BODY,
        COVER_OCR,
        RESOLVED_PLACES,
        COMBINED,
        NONE,
    }
}

internal fun fallbackPostTitle(request: PostTitleSelector.Request): String? {
    val resolvedPlaceCount = request.places.size
    val declaredCountMatches = request.declaredPlaceCount == null || request.declaredPlaceCount == resolvedPlaceCount
    val bodyTitle = groundedPostTitle(request.body, null)?.takeIf { declaredCountMatches }
    val places = request.places
    if (places.isEmpty()) return bodyTitle
    val region = request.sourceLocationTag
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf { it.length in 1..MAX_FALLBACK_REGION_LENGTH }
        ?: places.mapNotNull(PostTitleSelector.Place::city)
            .map(String::toTitleRegion)
            .distinct()
            .singleOrNull()

    val placeTitle = if (places.size == 1) {
        listOfNotNull(region, places.single().name).joinToString(" ")
    } else {
        val category = places.mapNotNull { it.category?.toTitleCategory() }.distinct().singleOrNull()
            ?: GENERIC_PLACE_CATEGORY
        val suffix = if (declaredCountMatches) "${places.size}곳" else COLLECTION_SUFFIX
        listOfNotNull(region, category, suffix).joinToString(" ")
    }
    return bodyTitle ?: placeTitle.validPostTitle()?.take(MAX_FINAL_TITLE_LENGTH)
}

internal fun String?.hasConsistentPlaceCount(declaredPlaceCount: Int?, resolvedPlaceCount: Int): Boolean {
    val title = this ?: return true
    val counts = TITLE_PLACE_COUNT_PATTERN.findAll(title).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
    if (counts.isEmpty()) return true
    return counts.all { it == resolvedPlaceCount } &&
        (declaredPlaceCount == null || declaredPlaceCount == resolvedPlaceCount)
}

private fun String.toTitleRegion(): String = removeSuffix("특별시")
    .removeSuffix("광역시")
    .removeSuffix("특별자치시")
    .removeSuffix("특별자치도")
    .removeSuffix("도")
    .ifBlank { this }

private fun String.toTitleCategory(): String? {
    val normalized = lowercase()
    return TITLE_CATEGORY_KEYWORDS.firstOrNull { (_, keywords) ->
        keywords.any(normalized::contains)
    }?.first
}

private const val MAX_FALLBACK_REGION_LENGTH = 12
internal const val MAX_FINAL_TITLE_LENGTH = 25
private const val GENERIC_PLACE_CATEGORY = "장소"
private const val COLLECTION_SUFFIX = "모음"
private val TITLE_PLACE_COUNT_PATTERN = Regex("(?<!\\d)(\\d{1,2})\\s*(?:곳|개|선|군데)")
private val TITLE_CATEGORY_KEYWORDS = listOf(
    "베이커리" to listOf("베이커리", "제과", "빵"),
    "카페" to listOf("카페", "커피"),
    "음식점" to listOf("음식점", "한식", "양식", "일식"),
    "술집" to listOf("주점", "술집"),
    "숙소" to listOf("숙박", "호텔", "펜션"),
    "상점" to listOf("상점", "쇼핑"),
)
