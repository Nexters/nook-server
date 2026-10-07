package org.every.nook.api.application.place

enum class PlaceCategoryGroup {
    CAFE,
    SHOPPING,
    RESTAURANT,
    BAKERY,
    BAR,
    LODGING,
    TOURISM,
    ETC,
    ;

    companion object {
        fun from(providerCategory: String?): PlaceCategoryGroup {
            val value = providerCategory?.takeIf(String::isNotBlank) ?: return ETC
            return when {
                BAR_KEYWORDS.any(value::contains) -> BAR
                BAKERY_KEYWORDS.any(value::contains) -> BAKERY
                CAFE_KEYWORDS.any(value::contains) -> CAFE
                SHOPPING_KEYWORDS.any(value::contains) -> SHOPPING
                RESTAURANT_KEYWORDS.any(value::contains) -> RESTAURANT
                LODGING_KEYWORDS.any(value::contains) -> LODGING
                TOURISM_KEYWORDS.any(value::contains) -> TOURISM
                else -> ETC
            }
        }

        private val CAFE_KEYWORDS = setOf("카페", "아이스크림", "찻집")
        private val SHOPPING_KEYWORDS = setOf("백화점", "쇼핑몰", "마트", "편의점", "의류", "화장품", "꽃집")
        private val RESTAURANT_KEYWORDS = setOf(
            "음식점",
            "한식",
            "일식",
            "중식당",
            "육류,고기요리",
            "양식",
            "샐러드",
            "패스트푸드",
            "생선회",
        )
        private val BAKERY_KEYWORDS = setOf("베이커리", "디저트", "브런치")
        private val BAR_KEYWORDS = setOf(
            "요리주점",
            "맥주,호프",
            "이자카야",
            "바(BAR)",
            "일식튀김,꼬치",
            "와인",
            "전통,민속주점",
            "술집",
        )
        private val LODGING_KEYWORDS = setOf("펜션", "전통숙소", "민박", "호텔")
        private val TOURISM_KEYWORDS = setOf("관광명소", "공원", "해변", "문화재", "사찰", "자연명소", "전망대")
    }
}
